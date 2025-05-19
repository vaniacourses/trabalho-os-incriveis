package net.originmobi.pdv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;

import net.originmobi.pdv.enumerado.notafiscal.NotaFiscalTipo;
import net.originmobi.pdv.model.*;
import net.originmobi.pdv.repository.notafiscal.NotaFiscalRepository;
import net.originmobi.pdv.service.EmpresaService;
import net.originmobi.pdv.service.EmpresaParametrosService;
import net.originmobi.pdv.service.PessoaService;
import net.originmobi.pdv.service.notafiscal.NotaFiscalService;
import net.originmobi.pdv.service.notafiscal.NotaFiscalTotaisServer;
import net.originmobi.pdv.model.EmpresaParametro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.boot.test.context.SpringBootTest;

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
        // Arrange
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.of(pessoa));
        when(notaFiscalRepository.buscaUltimaNota(anyInt())).thenReturn(1L);

        // Simula save da NotaFiscal
        NotaFiscal notaFiscal = new NotaFiscal();
        notaFiscal.setCodigo(123L);
        when(notaFiscalRepository.save(any(NotaFiscal.class))).thenReturn(notaFiscal);

        // Act
        String codigo = notaFiscalService.cadastrar(1L, "Venda de produto", NotaFiscalTipo.SAIDA);

        // Assert
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
        parametro.setSerie_nfe(0); // força erro
        when(empresaService.verificaEmpresaCadastrada()).thenReturn(Optional.of(empresa));
        when(pessoaService.buscaPessoa(1L)).thenReturn(Optional.of(pessoa));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            notaFiscalService.cadastrar(1L, "Venda de produto", NotaFiscalTipo.SAIDA);
        });

        assertEquals("Não existe série cadastrada para o modelo 55, verifique", exception.getMessage());
    }
}
