package net.originmobi.pdv.service.notafiscal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.sql.Date;
import java.time.Instant;

import net.originmobi.pdv.enumerado.TelefoneTipo;
import net.originmobi.pdv.enumerado.notafiscal.NotaFiscalTipo;
import net.originmobi.pdv.exception.NotaFiscalException;
import net.originmobi.pdv.model.*;
import net.originmobi.pdv.repository.notafiscal.NotaFiscalRepository;
import net.originmobi.pdv.service.EmpresaService;
import net.originmobi.pdv.service.PessoaService;
import org.junit.jupiter.api.*;
import org.mockito.*;

class NotaFiscalServiceTest {

    private NotaFiscalService notaFiscalService;

    @Mock
    private NotaFiscalRepository notaFiscalRepository;

    @Mock
    private EmpresaService empresaService;

    @Mock
    private NotaFiscalTotaisServer notaFiscalTotaisServer;

    @Mock
    private PessoaService pessoaService;

    private Empresa empresa;
    private Pessoa pessoa;
    private EmpresaParametro parametro;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        notaFiscalService = new NotaFiscalService(
            notaFiscalRepository,
            empresaService,
            notaFiscalTotaisServer,
            pessoaService
        );

        // --- Configuração dos dados de teste usando setters ---

        parametro = new EmpresaParametro();
        parametro.setSerie_nfe(1);
        parametro.setAmbiente(2); // Ambiente de homologação

        RegimeTributario rt = new RegimeTributario();
        rt.setTipoRegime(1); // Simples Nacional

        Pais pais = new Pais();
        pais.setNome("Brasil");
        pais.setCodigo_pais("1058");

        Estado estado = new Estado();
        estado.setSigla("RJ");
        estado.setNome("Rio de Janeiro");
        estado.setCodigoUF("33");
        estado.setPais(pais);

        Cidade cidade = new Cidade();
        cidade.setNome("Niterói");
        cidade.setEstado(estado);
        cidade.setCodigo_municipio("3303302");

        Endereco endereco = new Endereco();
        endereco.setRua("Rua Teste");
        endereco.setBairro("Bairro Teste");
        endereco.setNumero("123");
        endereco.setCep("24000000");
        endereco.setReferencia("Ref");
        endereco.setCidade(cidade);
        endereco.setData_cadastro(new Date(Instant.now().toEpochMilli()));

        empresa = new Empresa();
        empresa.setParametro(parametro);
        empresa.setCnpj("01234567000189");
        empresa.setIe("ISENTO");
        empresa.setRegime_tributario(rt);
        empresa.setEndereco(endereco);
        empresa.setNome("EMPRESA TESTE");
        empresa.setNome_fantasia("FANTASIA TESTE");

        Telefone telefone = new Telefone();
        telefone.setFone("21999999999");
        telefone.setTipo(TelefoneTipo.CELULAR);
        
        pessoa = new Pessoa();
        pessoa.setCpfcnpj("12345678901");
        pessoa.setTelefone(Collections.singletonList(telefone));
        pessoa.setEndereco(endereco);
        pessoa.setNome("PESSOA DESTINATARIO TESTE");
    }

    @Test
    void deveEmitirNotaFiscal() {
        // Arrange
        NotaFiscalService serviceSpy = spy(notaFiscalService);
        
        NotaFiscal notaFiscal = new NotaFiscal();
        notaFiscal.setEmissor(empresa);
        notaFiscal.setDestinatario(pessoa);
        notaFiscal.setNumero(123L);
        notaFiscal.setSerie(1);
        notaFiscal.setTipo_ambiente(2);
        
        NotaFiscalFinalidade finalidade = new NotaFiscalFinalidade();
        finalidade.setCodigo(1L);
        finalidade.setTipo(1);
        notaFiscal.setFinalidade(finalidade);

        FreteTipo frete = new FreteTipo();
        frete.setCodigo(9L);
        frete.setTipo(9);
        notaFiscal.setFreteTipo(frete);

        notaFiscal.setTotais(new NotaFiscalTotais());
		
        doReturn(true).when(serviceSpy).salvaXML(anyString(), anyString());

        // Act
        serviceSpy.emitir(notaFiscal);

        // Assert
        assertNotNull(notaFiscal.getChave_acesso());
        assertEquals(44, notaFiscal.getChave_acesso().length());
        verify(serviceSpy, times(1)).salvaXML(anyString(), eq(notaFiscal.getChave_acesso()));
        verify(notaFiscalRepository, times(1)).save(notaFiscal);
    }


    @Test
    void deveRetornarListaDeNotas() {
        NotaFiscal nota1 = new NotaFiscal();
        nota1.setCodigo(1L);
        NotaFiscal nota2 = new NotaFiscal();
        nota2.setCodigo(2L);

        when(notaFiscalRepository.findAll()).thenReturn(List.of(nota1, nota2));

        List<NotaFiscal> resultado = notaFiscalService.lista();

        assertEquals(2, resultado.size());
        assertEquals(1L, resultado.get(0).getCodigo());
        assertEquals(2L, resultado.get(1).getCodigo());
    }

    @Test
    void deveCadastrarNotaFiscalComSucesso() {
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.of(pessoa));
        when(notaFiscalRepository.buscaUltimaNota(anyInt())).thenReturn(1L);

        NotaFiscal notaFiscal = new NotaFiscal();
        notaFiscal.setCodigo(123L);
        when(notaFiscalRepository.save(any(NotaFiscal.class))).thenReturn(notaFiscal);

        String codigo = notaFiscalService.cadastrar(1L, "Venda de produto", NotaFiscalTipo.SAIDA);

        assertEquals("123", codigo);
        verify(notaFiscalTotaisServer, times(1)).cadastro(any(NotaFiscalTotais.class));
        verify(notaFiscalRepository, times(1)).save(any(NotaFiscal.class));
    }

    @Test
    void deveVerificarFreteEFinalidade() {
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.of(pessoa));
        when(notaFiscalRepository.buscaUltimaNota(anyInt())).thenReturn(1L);

        NotaFiscal notaFiscal = new NotaFiscal();
        notaFiscal.setCodigo(999L);
        when(notaFiscalRepository.save(any())).thenReturn(notaFiscal);

        notaFiscalService.cadastrar(1L, "Venda", NotaFiscalTipo.SAIDA);

        ArgumentCaptor<NotaFiscal> captor = ArgumentCaptor.forClass(NotaFiscal.class);
        verify(notaFiscalRepository).save(captor.capture());

        assertEquals(4L, captor.getValue().getFreteTipo().getCodigo());
        assertEquals(1L, captor.getValue().getFinalidade().getCodigo());
    }

    @Test
    void deveLancarErroAoSalvarTotais() {
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.of(pessoa));
        doThrow(new RuntimeException("Erro simulado")).when(notaFiscalTotaisServer).cadastro(any());

        NotaFiscalException ex = assertThrows(NotaFiscalException.class, () -> {
            notaFiscalService.cadastrar(1L, "Teste", NotaFiscalTipo.SAIDA);
        });

        assertEquals("Erro ao cadastrar os totais da nota fiscal", ex.getMessage());
    }

    @Test
    void deveLancarExcecaoSeNaoHouverEmpresa() {
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notaFiscalService.cadastrar(1L, "Venda", NotaFiscalTipo.SAIDA);
        });

        assertEquals("Nenhuma empresa cadastrada, verifique", exception.getMessage());
    }

    @Test
    void deveLancarExcecaoSeNaoHouverPessoa() {
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notaFiscalService.cadastrar(1L, "Venda", NotaFiscalTipo.SAIDA);
        });

        assertEquals("Favor, selecione o destinatário", exception.getMessage());
    }

    @Test
    void deveLancarExcecaoSeSerieNfeForZero() {
        parametro.setSerie_nfe(0);
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.of(pessoa));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notaFiscalService.cadastrar(1L, "Venda", NotaFiscalTipo.SAIDA);
        });

        assertEquals("Não existe série cadastrada para o modelo 55, verifique", exception.getMessage());
    }

    @Test
    void deveCalcularDigitoVerificador() {
        assertEquals(0, NotaFiscalService.geraDV("000000000"));
        Integer dv = NotaFiscalService.geraDV("123456789");
        assertNotNull(dv);
        assertTrue(dv >= 0 && dv <= 9);
    }

    @Test
    void deveSalvarXmlNoDisco() throws Exception {
        String xml = "<nfe>exemplo</nfe>";
        String chave = "12345678901234567890123456789012345678901234";
        Path path = Paths.get(new File(".").getCanonicalPath() + "/src/main/resources/xmlNfe/" + chave + ".xml");
        Files.deleteIfExists(path);

        boolean resultado = notaFiscalService.salvaXML(xml, chave);

        assertTrue(resultado);
        assertTrue(Files.exists(path));
        Files.deleteIfExists(path);
    }

    @Test
    void deveRemoverXmlExistente() throws Exception {
        String chave = "teste_delete";
        Path path = Paths.get(new File(".").getCanonicalPath() + "/src/main/resources/xmlNfe/" + chave + ".xml");
        File file = path.toFile();
        file.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("<xml>exemplo</xml>");
        }

        assertTrue(file.exists());
        boolean removido = notaFiscalService.removeXml(chave);
        assertTrue(removido);
        assertFalse(file.exists());
    }

    @Test
    void deveRetornarFalseQuandoFalharAoSalvarXml() throws Exception {
        NotaFiscalService service = spy(new NotaFiscalService(
            notaFiscalRepository, empresaService, notaFiscalTotaisServer, pessoaService
        ));

        doThrow(new IOException("erro simulado"))
            .when(service).createPrintWriter(any(File.class));

        boolean resultado = service.salvaXML("<nfe>erro</nfe>", "erro");

        assertFalse(resultado);
    }

    @Test
    void totalNotaFiscalEmitidasRetornaValorEsperado() {
        when(notaFiscalRepository.totalNotaFiscalEmitidas()).thenReturn(5);
        assertEquals(5, notaFiscalService.totalNotaFiscalEmitidas());
    }

    @Test
    void deveRetornarListaVaziaDeNotas() {
        when(notaFiscalRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        assertTrue(notaFiscalService.lista().isEmpty());
    }

    @Test
    void deveRetornarFalseSeArquivoNaoExistirAoRemoverXml() {
        String chave = "nao_existe";
        boolean resultado = notaFiscalService.removeXml(chave);
        assertTrue(resultado);
    }

    @Test
    void deveBuscarNotaFiscalPorCodigo() {
        NotaFiscal nota = new NotaFiscal();
        nota.setCodigo(123L);
        when(notaFiscalRepository.findById(123L)).thenReturn(Optional.of(nota));

        Optional<NotaFiscal> resultado = notaFiscalService.busca(123L);

        assertTrue(resultado.isPresent());
        assertEquals(123L, resultado.get().getCodigo());
    }

    @Test
    void deveResetarPesoAoChegarNove() {
        NotaFiscalService.geraDV("123456789");
    }

    @Test
    void deveCalcularDVComRestoIgualUm() {
        String codigo = "000000006";
        Integer dv = NotaFiscalService.geraDV(codigo);
        assertEquals(0, dv);
    }

    @Test
    void deveSalvarXmlComConteudoCorreto() throws Exception {
        String xml = "<nfe>conteudo</nfe>";
        String chave = "xml_conteudo_teste";
        Path path = Paths.get(new File(".").getCanonicalPath() + "/src/main/resources/xmlNfe/" + chave + ".xml");
        Files.deleteIfExists(path);

        notaFiscalService.salvaXML(xml, chave);
        
        assertTrue(Files.exists(path));
        String conteudo = Files.readString(path);
        assertEquals(xml, conteudo);

        Files.deleteIfExists(path);
    }
}