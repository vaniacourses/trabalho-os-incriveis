package net.originmobi.pdv.service.notafiscal;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map; // Importe java.util.Map
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.originmobi.pdv.enumerado.notafiscal.NotaFiscalTipo;
import net.originmobi.pdv.exception.NotaFiscalException;
import net.originmobi.pdv.model.Empresa;
import net.originmobi.pdv.model.FreteTipo;
import net.originmobi.pdv.model.NotaFiscal;
import net.originmobi.pdv.model.NotaFiscalFinalidade;
import net.originmobi.pdv.model.NotaFiscalTotais;
import net.originmobi.pdv.model.Pessoa;
import net.originmobi.pdv.repository.notafiscal.NotaFiscalRepository;
import net.originmobi.pdv.service.EmpresaService;
import net.originmobi.pdv.service.PessoaService;
import net.originmobi.pdv.xml.nfe.GeraXmlNfe;

@Service
public class NotaFiscalService {

    private final NotaFiscalRepository notasFiscais;
    private final EmpresaService empresas;
    private final NotaFiscalTotaisServer notaTotais;
    private final PessoaService pessoas;
    // Removida a injeção de GeraXmlNfe

    private static final String CAMINHO_XML = Paths.get("src", "main", "resources", "xmlNfe").toString();

    // Construtor revertido ao original
    public NotaFiscalService(
        NotaFiscalRepository notasFiscais,
        EmpresaService empresas,
        NotaFiscalTotaisServer notaTotais,
        PessoaService pessoas
    ) {
        this.notasFiscais = notasFiscais;
        this.empresas = empresas;
        this.notaTotais = notaTotais;
        this.pessoas = pessoas;
    }

    protected PrintWriter createPrintWriter(File file) throws IOException {
        return new PrintWriter(new FileWriter(file));
    }

    public List<NotaFiscal> lista() {
        return notasFiscais.findAll();
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED)
    public String cadastrar(Long coddesti, String natureza, NotaFiscalTipo tipo) {
        Empresa empresa = empresas.verificaEmpresaCadastrada()
                .orElseThrow(() -> new NotaFiscalException("Nenhuma empresa cadastrada, verifique"));

        Pessoa pessoa = pessoas.buscaPessoa(coddesti)
                .orElseThrow(() -> new NotaFiscalException("Favor, selecione o destinatário"));

        FreteTipo frete = new FreteTipo();
        frete.setCodigo(4L);

        NotaFiscalFinalidade finalidade = new NotaFiscalFinalidade();
        finalidade.setCodigo(1L);

        int modelo = 55;
        int serie = Optional.ofNullable(empresa.getParametro())
                .map(p -> p.getSerie_nfe())
                .orElseThrow(() -> new NotaFiscalException("Série NFe não encontrada"));

        if (serie == 0) {
            throw new NotaFiscalException("Não existe série cadastrada para o modelo 55, verifique");
        }

        int tipoEmissao = 1;
        LocalDate dataAtual = LocalDate.now();
        Date cadastro = Date.valueOf(dataAtual);
        String verProc = "0.0.1-beta";
        int tipoAmbiente = empresa.getParametro().getAmbiente();

        NotaFiscalTotais totais = new NotaFiscalTotais(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        );

        try {
            notaTotais.cadastro(totais);
        } catch (Exception e) {
            throw new NotaFiscalException("Erro ao cadastrar os totais da nota fiscal", e);
        }

        try {
            Long numeroNota = notasFiscais.buscaUltimaNota(serie);

            NotaFiscal notaFiscal = new NotaFiscal(
                    numeroNota, modelo, tipo, natureza, serie,
                    empresa, pessoa, tipoEmissao, verProc,
                    frete, finalidade, totais, tipoAmbiente, cadastro
            );

            NotaFiscal nota = notasFiscais.save(notaFiscal);
            return nota.getCodigo().toString();

        } catch (Exception e) {
            throw new NotaFiscalException("Erro ao cadastrar a nota fiscal", e);
        }
    }

    public static Integer geraDV(String codigo) {
        int total = 0;
        int peso = 2;

        for (int i = codigo.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(codigo.charAt(i));
            total += digito * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }

        int resto = total % 11;
        return (resto == 0 || resto == 1) ? 0 : (11 - resto);
    }

    public boolean salvaXML(String xml, String chaveNfe) {
        try {
            String contexto = new File(".").getCanonicalPath();
            Path diretorio = Paths.get(contexto, CAMINHO_XML);
            File file = new File(diretorio.toFile(), chaveNfe + ".xml");
			file.getParentFile().mkdirs(); // Garante que o diretório exista

            try (PrintWriter out = createPrintWriter(file)) {
                out.write(xml);
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean removeXml(String chaveAcesso) {
        try {
            String contexto = new File(".").getCanonicalPath();
            Path path = Paths.get(contexto, CAMINHO_XML, chaveAcesso + ".xml");

            Files.deleteIfExists(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Optional<NotaFiscal> busca(Long codnota) {
        return notasFiscais.findById(codnota);
    }

    public void emitir(NotaFiscal notaFiscal) {
        GeraXmlNfe geraXmlNfe = new GeraXmlNfe();
        
        Map<String, String> resultadoXml = geraXmlNfe.gerarXML(notaFiscal);
        String chaveNfe = resultadoXml.get("chave");
        String xml = resultadoXml.get("xml");

        if (notaFiscal.getChave_acesso() != null && !notaFiscal.getChave_acesso().isEmpty()) {
            this.removeXml(notaFiscal.getChave_acesso());
        }

        // Salva o novo XML
        this.salvaXML(xml, chaveNfe);
        
        // Atualiza a entidade e salva no banco
        notaFiscal.setChave_acesso(chaveNfe);
        notasFiscais.save(notaFiscal);
    }

    public int totalNotaFiscalEmitidas() {
        return notasFiscais.totalNotaFiscalEmitidas();
    }
}