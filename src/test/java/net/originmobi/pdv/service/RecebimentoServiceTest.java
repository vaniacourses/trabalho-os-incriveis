package net.originmobi.pdv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import net.originmobi.pdv.controller.TituloService;
import net.originmobi.pdv.model.Caixa;
import net.originmobi.pdv.model.CaixaLancamento;
import net.originmobi.pdv.model.Parcela;
import net.originmobi.pdv.model.Pessoa;
import net.originmobi.pdv.model.Receber;
import net.originmobi.pdv.model.Recebimento;
import net.originmobi.pdv.model.Titulo;
import net.originmobi.pdv.model.TituloTipo;
import net.originmobi.pdv.model.Usuario;
import net.originmobi.pdv.repository.RecebimentoRepository;
import net.originmobi.pdv.service.cartao.CartaoLancamentoService;

@ExtendWith(MockitoExtension.class)
class RecebimentoServiceTest {

    @InjectMocks
    private RecebimentoService recebimentoService;

    @Mock
    private PessoaService pessoas;

    @Mock
    private ParcelaService parcelas;

    @Mock
    private RecebimentoRepository recebimentos;

    @Mock
    private RecebimentoParcelaService receParcelas;

    @Mock
    private UsuarioService usuarios;

    @Mock
    private CaixaService caixas;

    @Mock
    private CaixaLancamentoService lancamentos;

    @Mock
    private TituloService titulos;

    @Mock
    private CartaoLancamentoService cartaoLancamentos;

    @Test
    void testAbrirRecebimento() {
        Long codPessoa = 1L;
        Long codParcela = 10L;

        Pessoa pessoaMock = new Pessoa();
        pessoaMock.setCodigo(codPessoa);

        Parcela parcelaMock = new Parcela();
        parcelaMock.setCodigo(codParcela);
        parcelaMock.setQuitado(0);
        parcelaMock.setValor_restante(100.0);
        parcelaMock.setReceber(new Receber());
        parcelaMock.getReceber().setPessoa(pessoaMock);

        when(pessoas.buscaPessoa(codPessoa)).thenReturn(Optional.of(pessoaMock));
        when(parcelas.busca(codParcela)).thenReturn(parcelaMock);
        when(recebimentos.save(any(Recebimento.class))).thenAnswer(invocation -> {
            Recebimento r = invocation.getArgument(0);
            r.setCodigo(123L);
            return r;
        });

        String codigo = recebimentoService.abrirRecebimento(codPessoa, new String[]{String.valueOf(codParcela)});

        assertEquals("123", codigo);
    }
    
    @Test
    void testAbrirRecebimentoComParcelaQuitada() {
        Long codPessoa = 1L;
        Long codParcela = 20L;

        Pessoa pessoaMock = new Pessoa();
        pessoaMock.setCodigo(codPessoa);

        Parcela parcelaQuitada = new Parcela();
        parcelaQuitada.setCodigo(codParcela);
        parcelaQuitada.setQuitado(1);
        parcelaQuitada.setReceber(new Receber());
        parcelaQuitada.getReceber().setPessoa(pessoaMock);

        when(parcelas.busca(codParcela)).thenReturn(parcelaQuitada);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            recebimentoService.abrirRecebimento(codPessoa, new String[]{String.valueOf(codParcela)});
        });

        assertEquals("Parcela 20 já esta quitada, verifique.", exception.getMessage());
    }
    
    @Test
    void testAbrirRecebimentoComParcelaDeOutroCliente() {
        Long codPessoa = 1L;
        Long codParcela = 30L;

        Pessoa pessoaDiferente = new Pessoa();
        pessoaDiferente.setCodigo(2L);

        Parcela parcela = new Parcela();
        parcela.setCodigo(codParcela);
        parcela.setQuitado(0);
        parcela.setValor_restante(50.0);
        parcela.setReceber(new Receber());
        parcela.getReceber().setPessoa(pessoaDiferente);

        when(parcelas.busca(codParcela)).thenReturn(parcela);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            recebimentoService.abrirRecebimento(codPessoa, new String[]{String.valueOf(codParcela)});
        });

        assertEquals("A parcela 30 não pertence ao cliente selecionado", exception.getMessage());
    }
    
    @Test
    void testAbrirRecebimentoComPessoaInexistente() {
        Long codPessoa = 99L;
        Long codParcela = 100L;

        Pessoa pessoaMock = new Pessoa();
        pessoaMock.setCodigo(codPessoa);

        Parcela parcelaMock = new Parcela();
        parcelaMock.setCodigo(codParcela);
        parcelaMock.setQuitado(0);
        parcelaMock.setValor_restante(100.0);
        parcelaMock.setReceber(new Receber());
        parcelaMock.getReceber().setPessoa(pessoaMock);

        when(parcelas.busca(codParcela)).thenReturn(parcelaMock);
        when(pessoas.buscaPessoa(codPessoa)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            recebimentoService.abrirRecebimento(codPessoa, new String[]{String.valueOf(codParcela)});
        });

        assertEquals("Cliente não encontrado", exception.getMessage());
    }

    @Test
    void testReceberComSucesso() {
        Long codRecebimento = 1L;
        Double vlRecebido = 100.0;
        Double vlAcrescimo = 0.0;
        Double vlDesconto = 0.0;
        Long codTitulo = 2L;

        TituloTipo tipoMock = new TituloTipo();
        tipoMock.setCodigo(1L);
        tipoMock.setDescricao("DINHEIRO");
        tipoMock.setSigla("DIN");
        Titulo tituloMock = new Titulo();
        tituloMock.setCodigo(codTitulo);
        tituloMock.setTipo(tipoMock);

        Pessoa pessoaMock = new Pessoa();
        pessoaMock.setCodigo(1L);

        Recebimento recebimentoMock = new Recebimento();
        recebimentoMock.setCodigo(codRecebimento);
        recebimentoMock.setPessoa(pessoaMock);
        recebimentoMock.setValor_total(100.0);
        Parcela parcelaMock = new Parcela();
        parcelaMock.setCodigo(10L);
        parcelaMock.setValor_restante(100.0);

        Usuario usuarioMock = new Usuario();
        usuarioMock.setCodigo(1L);
        Caixa caixaMock = new Caixa();
        caixaMock.setCodigo(1L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("1", null)
        );
        
        when(recebimentos.findById(codRecebimento)).thenReturn(Optional.of(recebimentoMock));
        when(titulos.busca(codTitulo)).thenReturn(Optional.of(tituloMock));
        when(receParcelas.parcelasDoReceber(codRecebimento)).thenReturn(List.of(parcelaMock));
        when(usuarios.buscaUsuario("1")).thenReturn(usuarioMock);
        when(caixas.caixaAberto()).thenReturn(Optional.of(caixaMock));
        when(lancamentos.lancamento(any())).thenReturn("OK");
        when(recebimentos.save(any())).thenReturn(recebimentoMock);

        String resultado = recebimentoService.receber(codRecebimento, vlRecebido, vlAcrescimo, vlDesconto, codTitulo);

        assertEquals("Recebimento realizado com sucesso", resultado);
    }
    
    @Test
    void testReceberComValorSuperiorAoPermitido() {
        Long codRecebimento = 3L;
        Long codTitulo = 3L;

        Pessoa pessoaMock = new Pessoa();
        pessoaMock.setCodigo(1L);

        Recebimento recebimentoMock = new Recebimento();
        recebimentoMock.setCodigo(codRecebimento);
        recebimentoMock.setPessoa(pessoaMock);
        recebimentoMock.setValor_total(50.0);

        TituloTipo tipoMock = new TituloTipo();
        tipoMock.setCodigo(1L);
        tipoMock.setDescricao("DINHEIRO");
        tipoMock.setSigla("DIN");

        Titulo tituloMock = new Titulo();
        tituloMock.setCodigo(codTitulo);
        tituloMock.setTipo(tipoMock);

        when(recebimentos.findById(codRecebimento)).thenReturn(Optional.of(recebimentoMock));
        when(titulos.busca(codTitulo)).thenReturn(Optional.of(tituloMock));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            recebimentoService.receber(codRecebimento, 100.0, 0.0, 0.0, codTitulo);
        });

        assertEquals("Valor de recebimento é superior aos títulos", exception.getMessage());
    }
    
    @Test
    void testReceberSemParcelas() {
        Long codRecebimento = 5L;
        Long codTitulo = 5L;

        Pessoa pessoaMock = new Pessoa();
        pessoaMock.setCodigo(1L);

        Recebimento recebimentoMock = new Recebimento();
        recebimentoMock.setCodigo(codRecebimento);
        recebimentoMock.setPessoa(pessoaMock);
        recebimentoMock.setValor_total(100.0);

        TituloTipo tipoMock = new TituloTipo();
        tipoMock.setCodigo(1L);
        tipoMock.setDescricao("DINHEIRO");
        tipoMock.setSigla("DIN");

        Titulo tituloMock = new Titulo();
        tituloMock.setCodigo(codTitulo);
        tituloMock.setTipo(tipoMock);

        when(recebimentos.findById(codRecebimento)).thenReturn(Optional.of(recebimentoMock));
        when(titulos.busca(codTitulo)).thenReturn(Optional.of(tituloMock));
        when(receParcelas.parcelasDoReceber(codRecebimento)).thenReturn(List.of());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            recebimentoService.receber(codRecebimento, 100.0, 0.0, 0.0, codTitulo);
        });

        assertEquals("Recebimento não possue parcelas", exception.getMessage());
    }

    @Test
    void testRemoverRecebimentoNaoProcessado() {
        Long codigoRecebimento = 1L;

        Recebimento recebimentoMock = new Recebimento();
        recebimentoMock.setCodigo(codigoRecebimento);
        recebimentoMock.setData_processamento(null);

        when(recebimentos.findById(codigoRecebimento)).thenReturn(Optional.of(recebimentoMock));
        doNothing().when(recebimentos).deleteById(codigoRecebimento);

        String resultado = recebimentoService.remover(codigoRecebimento);

        assertEquals("removido com sucesso", resultado);
        verify(recebimentos).deleteById(codigoRecebimento);
    }

    @Test
    void testRemoverRecebimentoJaProcessado() {
        Long codigoRecebimento = 2L;

        Recebimento recebimentoMock = new Recebimento();
        recebimentoMock.setCodigo(codigoRecebimento);
        recebimentoMock.setData_processamento(new java.sql.Timestamp(System.currentTimeMillis()));

        when(recebimentos.findById(codigoRecebimento)).thenReturn(Optional.of(recebimentoMock));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            recebimentoService.remover(codigoRecebimento);
        });

        assertEquals("Esse recebimento não pode ser removido, pois ele já esta processado", exception.getMessage());
        verify(recebimentos, never()).deleteById(any());
    }
}
