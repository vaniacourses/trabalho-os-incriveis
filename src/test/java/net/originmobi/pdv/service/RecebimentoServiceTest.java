package net.originmobi.pdv.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
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
import net.originmobi.pdv.exception.RecebimentoNotFoundException;
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
        Long codParcela1 = 10L, codParcela2 = 11L;

        Pessoa pessoaMock = new Pessoa();
        pessoaMock.setCodigo(codPessoa);

        Parcela parcelaMock1 = new Parcela();
        parcelaMock1.setCodigo(codParcela1);
        parcelaMock1.setQuitado(0);
        parcelaMock1.setValor_restante(100.0);
        parcelaMock1.setReceber(new Receber());
        parcelaMock1.getReceber().setPessoa(pessoaMock);

        Parcela parcelaMock2 = new Parcela();
        parcelaMock2.setCodigo(codParcela2);
        parcelaMock2.setQuitado(0);
        parcelaMock2.setValor_restante(200.0);
        parcelaMock2.setReceber(new Receber());
        parcelaMock2.getReceber().setPessoa(pessoaMock);

        when(pessoas.buscaPessoa(codPessoa)).thenReturn(Optional.of(pessoaMock));
        when(parcelas.busca(codParcela1)).thenReturn(parcelaMock1);
        when(parcelas.busca(codParcela2)).thenReturn(parcelaMock2);
        when(recebimentos.save(any(Recebimento.class))).thenAnswer(invocation -> {
            Recebimento r = invocation.getArgument(0);
            r.setCodigo(123L);
            assertEquals(300.0, r.getValor_total(), 0.01);
            return r;
        });

        String codigo = recebimentoService.abrirRecebimento(codPessoa,
                new String[]{String.valueOf(codParcela1), String.valueOf(codParcela2)});

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
        Long codRecebimento = 1L, codTitulo = 2L;
        Double vlRecebido = 100.0, vlAcrescimo = 10.0, vlDesconto = 5.0;

        TituloTipo tipoMock = new TituloTipo(); tipoMock.setSigla("DIN");
        Titulo tituloMock = new Titulo(); tituloMock.setCodigo(codTitulo); tituloMock.setTipo(tipoMock);

        Recebimento recebimentoMock = new Recebimento();
        recebimentoMock.setCodigo(codRecebimento);
        recebimentoMock.setValor_total(100.0);

        Parcela parcelaMock = new Parcela(); parcelaMock.setCodigo(10L); parcelaMock.setValor_restante(100.0);

        Usuario usuarioMock = new Usuario(); usuarioMock.setCodigo(1L);
        Caixa caixaMock = new Caixa(); caixaMock.setCodigo(1L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("1", null));

        when(recebimentos.findById(codRecebimento)).thenReturn(Optional.of(recebimentoMock));
        when(titulos.busca(codTitulo)).thenReturn(Optional.of(tituloMock));
        when(receParcelas.parcelasDoReceber(codRecebimento)).thenReturn(List.of(parcelaMock));
        when(usuarios.buscaUsuario("1")).thenReturn(usuarioMock);
        when(caixas.caixaAberto()).thenReturn(Optional.of(caixaMock));
        when(lancamentos.lancamento(any())).thenReturn("OK");
        when(recebimentos.save(any())).thenReturn(recebimentoMock);

        String resultado = recebimentoService.receber(codRecebimento, vlRecebido, vlAcrescimo, vlDesconto, codTitulo);

        assertEquals("Recebimento realizado com sucesso", resultado);

        assertEquals(vlRecebido, recebimentoMock.getValor_recebido());
        assertEquals(vlAcrescimo, recebimentoMock.getValor_acrescimo());
        assertEquals(vlDesconto, recebimentoMock.getValor_desconto());
        assertNotNull(recebimentoMock.getData_processamento());

        verify(parcelas, atLeastOnce()).receber(anyLong(), anyDouble(), anyDouble(), anyDouble());
        verify(lancamentos).lancamento(any(CaixaLancamento.class));
        verify(recebimentos).save(recebimentoMock);
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

        assertEquals("Recebimento não possui parcelas", exception.getMessage());
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
    
    @Test
    void testReceberComValorZero() {
        Long codRecebimento = 7L;
        Long codTitulo = 8L;

        RecebimentoNotFoundException ex = assertThrows(RecebimentoNotFoundException.class, () -> {
            recebimentoService.receber(codRecebimento, 0.0, 0.0, 0.0, codTitulo);
        });

        assert(ex.getMessage().contains("Recebimento não encontrado"));
    }

    @Test
    void testReceberComTituloCartao() {
        Long codRecebimento = 9L;
        Long codTitulo = 10L;

        Recebimento recebimento = new Recebimento();
        recebimento.setCodigo(codRecebimento);
        recebimento.setValor_total(50.0);

        Titulo titulo = new Titulo();
        TituloTipo tipo = new TituloTipo();
        tipo.setSigla("CARTDEB");
        titulo.setTipo(tipo);

        Parcela parcela = new Parcela();
        parcela.setCodigo(1L);
        parcela.setValor_restante(50.0);

        when(recebimentos.findById(codRecebimento)).thenReturn(Optional.of(recebimento));
        when(titulos.busca(codTitulo)).thenReturn(Optional.of(titulo));
        when(receParcelas.parcelasDoReceber(codRecebimento)).thenReturn(List.of(parcela));
        doNothing().when(cartaoLancamentos).lancamento(any(), any());
        when(recebimentos.save(any())).thenReturn(recebimento);

        String resultado = recebimentoService.receber(codRecebimento, 50.0, 0.0, 0.0, codTitulo);
        assert(resultado.contains("sucesso"));
    }

    @Test
    void testAbrirRecebimentoComErroNoSave() {
        Long codPessoa = 11L;
        Long codParcela = 12L;

        Pessoa pessoa = new Pessoa();
        pessoa.setCodigo(codPessoa);

        Parcela parcela = new Parcela();
        parcela.setCodigo(codParcela);
        parcela.setQuitado(0);
        parcela.setValor_restante(100.0);
        Receber receber = new Receber();
        receber.setPessoa(pessoa);
        parcela.setReceber(receber);

        when(parcelas.busca(codParcela)).thenReturn(parcela);
        when(pessoas.buscaPessoa(codPessoa)).thenReturn(Optional.of(pessoa));
        when(recebimentos.save(any())).thenThrow(new RuntimeException("erro db"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            recebimentoService.abrirRecebimento(codPessoa, new String[]{String.valueOf(codParcela)});
        });

        assert(ex.getMessage().contains("Erro ao receber"));
    }

    @Test
    void testRemoverComErroNoDelete() {
        Long codRecebimento = 13L;

        Recebimento recebimento = new Recebimento();
        recebimento.setCodigo(codRecebimento);

        when(recebimentos.findById(codRecebimento)).thenReturn(Optional.of(recebimento));
        doThrow(new RuntimeException("erro delete")).when(recebimentos).deleteById(codRecebimento);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            recebimentoService.remover(codRecebimento);
        });

        assert(ex.getMessage().contains("Erro ao remover orçamento"));
    }

    @Test
    void testReceberComProcessamentoJaFeito() {
        Long codRecebimento = 14L;
        Long codTitulo = 15L;

        Recebimento recebimento = new Recebimento();
        recebimento.setCodigo(codRecebimento);
        recebimento.setData_processamento(new Timestamp(System.currentTimeMillis()));

        when(recebimentos.findById(codRecebimento)).thenReturn(Optional.of(recebimento));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            recebimentoService.receber(codRecebimento, 10.0, 0.0, 0.0, codTitulo);
        });

        assert(ex.getMessage().contains("Recebimento já está fechado"));
    }
}
