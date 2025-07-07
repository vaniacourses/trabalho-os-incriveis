package net.originmobi.pdv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import net.originmobi.pdv.model.*;
import net.originmobi.pdv.repository.notafiscal.NotaFiscalRepository;
import net.originmobi.pdv.service.notafiscal.NotaFiscalService;
import net.originmobi.pdv.service.notafiscal.NotaFiscalTotaisServer;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.boot.test.context.SpringBootTest;

import net.originmobi.pdv.enumerado.notafiscal.NotaFiscalTipo;

@SpringBootTest
class NotaFiscalServiceTest {

    @InjectMocks
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

        // Empresa e parâmetro
        empresa = new Empresa();
        parametro = new EmpresaParametro();
        parametro.setSerie_nfe(1);
        parametro.setAmbiente(1);
        empresa.setParametro(parametro);

        // Pessoa
        pessoa = new Pessoa();
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
    void deveLancarExcecaoSeNaoHouverEmpresa() {
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notaFiscalService.cadastrar(1L, "Venda de produto", NotaFiscalTipo.SAIDA);
        });

        assertEquals("Nenhuma empresa cadastrada, verifique", exception.getMessage());
    }

    @Test
    void deveLancarExcecaoSeNaoHouverPessoa() {
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notaFiscalService.cadastrar(1L, "Venda de produto", NotaFiscalTipo.SAIDA);
        });

        assertEquals("Favor, selecione o destinatário", exception.getMessage());
    }

    @Test
    void deveLancarExcecaoSeSerieNfeForZero() {
        parametro.setSerie_nfe(0);
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.of(pessoa));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notaFiscalService.cadastrar(1L, "Venda de produto", NotaFiscalTipo.SAIDA);
        });

        assertEquals("Não existe série cadastrada para o modelo 55, verifique", exception.getMessage());
    }

    @Test
    void deveCalcularDigitoVerificador() {
        Integer dv = notaFiscalService.geraDV("123456789");
        assertNotNull(dv);
        assertTrue(dv >= 0 && dv <= 9);
    }

    @Test
    void deveSalvarXmlNoDisco() throws Exception {
        String xml = "<nfe>exemplo</nfe>";
        String chave = "12345678901234567890123456789012345678901234";

        notaFiscalService.salvaXML(xml, chave);

        Path path = Paths.get(System.getProperty("user.dir") + "/src/main/resources/xmlNfe/" + chave + ".xml");
        assertTrue(Files.exists(path));

        Files.deleteIfExists(path); // limpeza
    }

    @Test
    void deveRemoverXmlExistente() throws Exception {
        String chave = "teste_delete";
        Path path = Paths.get(System.getProperty("user.dir") + "/src/main/resources/xmlNfe/" + chave + ".xml");
        File file = path.toFile();
        file.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("<xml>exemplo</xml>");
        }
        assertTrue(file.exists());

        notaFiscalService.removeXml(chave);

        assertFalse(file.exists());
    }

    @Test
    void deveEmitirNotaFiscalComSucesso() {
        NotaFiscal notaFiscal = new NotaFiscal();
        notaFiscal.setCodigo(999L);

        // Espera-se que o método gere a chave e salve a nota
        when(notaFiscalRepository.save(any(NotaFiscal.class))).thenReturn(notaFiscal);

        notaFiscalService.emitir(notaFiscal);

        verify(notaFiscalRepository, times(1)).save(notaFiscal);
        assertNotNull(notaFiscal.getChave_acesso());
    }

    @Test
    void totalNotaFiscalEmitidasRetornaValorEsperado() {
        when(notaFiscalRepository.totalNotaFiscalEmitidas()).thenReturn(5);
        assertEquals(5, notaFiscalService.totalNotaFiscalEmitidas());
    }
}
