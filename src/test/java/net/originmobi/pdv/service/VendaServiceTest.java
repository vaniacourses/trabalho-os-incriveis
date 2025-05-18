package net.originmobi.pdv.service;

import net.originmobi.pdv.controller.TituloService;
import net.originmobi.pdv.enumerado.EntradaSaida;
import net.originmobi.pdv.enumerado.VendaSituacao;
import net.originmobi.pdv.enumerado.caixa.EstiloLancamento;
import net.originmobi.pdv.enumerado.caixa.TipoLancamento;
import net.originmobi.pdv.filter.VendaFilter;
import net.originmobi.pdv.model.*;
import net.originmobi.pdv.repository.VendaRepository;
import net.originmobi.pdv.service.UsuarioService;
import net.originmobi.pdv.service.VendaService;
import net.originmobi.pdv.service.cartao.CartaoLancamentoService;
import net.originmobi.pdv.singleton.Aplicacao;
import net.originmobi.pdv.utilitarios.DataAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

public class VendaServiceTest {

    @InjectMocks
    private VendaService vendaService;

    @Mock
    private UsuarioService usuarioService;
    @Mock
    private VendaRepository vendas;

    @Mock
    private VendaProdutoService vendaProdutos;

    @Mock private PagamentoTipoService formaPagamentos;
    @Mock private TituloService tituloService;
    @Mock private CaixaService caixas;
    @Mock private ProdutoService produtos;
    @Mock private ReceberService receberServ;
    @Mock private CartaoLancamentoService cartaoLancamento;
    @Mock private CaixaLancamentoService lancamentos;
    @Mock private ParcelaService parcelas;

    private final Long codVenda = 1L;
    private final Long codProduto = 10L;
    private final Double vlBalanca = 5.0;
    private final Long posicaoProd = 2L;


    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("testuser", "password", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(usuarioService.buscaUsuario(anyString())).thenReturn(new Usuario());
    }

    @Test
    public void abreVendaTest() {
        Venda venda = mock(Venda.class);
        when(venda.getCodigo()).thenReturn(1234L);

        Venda nullVenda = mock(Venda.class);
        when(nullVenda.getCodigo()).thenReturn(null);

        assertEquals(1234L, vendaService.abreVenda(venda));
        assertNull(vendaService.abreVenda(nullVenda));
    }

    @Test
    public void buscaPorCodigoTest() {
        VendaFilter vendaFilter = new VendaFilter();
        vendaFilter.setCodigo(1234L);
        String situacao = "ABERTA";
        Pageable pageable = mock(Pageable.class);

        Venda fakeVenda = new Venda();
        fakeVenda.setCodigo(1234L);

        Page<Venda> fakePage = new PageImpl<>(List.of(fakeVenda));

        when(vendas.findByCodigo(eq(1234L), any(Pageable.class))).thenReturn(fakePage);

        var result = vendaService.busca(vendaFilter, situacao, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1234L, result.getContent().get(0).getCodigo());
    }

    @Test
    public void buscaPorSituacaoTest() {
        VendaFilter filter = new VendaFilter();
        String situacao = "ABERTA";
        Pageable pageable = mock(Pageable.class);


        Venda fakeVenda = new Venda();
        fakeVenda.setCodigo(1L);
        Page<Venda> fakePage = new PageImpl<>(List.of(fakeVenda));

        when(vendas.findBySituacaoEquals(eq(VendaSituacao.ABERTA), eq(pageable)))
                .thenReturn(fakePage);

        Page<Venda> result = vendaService.busca(filter, situacao, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1L, result.getContent().get(0).getCodigo());
    }

    @Test
    public void buscaTest() {
        VendaFilter vendaFilter = new VendaFilter();
        vendaFilter.setCodigo(1234L);
        String situacao = "ABERTA";
        Pageable pageable = mock(Pageable.class);

        when(vendas.findByCodigo(eq(1234L), any(Pageable.class))).thenReturn(null);

        assertNull(vendaService.busca(vendaFilter, situacao, pageable));
    }

    @Test
    public void testAddProdutoVendaAberta() {
        when(vendas.verificaSituacao(codVenda)).thenReturn(VendaSituacao.ABERTA.toString());

        doNothing().when(vendaProdutos).salvar(any(VendaProduto.class));

        String result = vendaService.addProduto(codVenda, codProduto, vlBalanca);

        assertEquals("ok", result);
        verify(vendaProdutos, times(1)).salvar(any(VendaProduto.class));
    }

    @Test
    public void testAddProdutoVendaFechada() {
        when(vendas.verificaSituacao(codVenda)).thenReturn(VendaSituacao.FECHADA.toString());

        String result = vendaService.addProduto(codVenda, codProduto, vlBalanca);

        assertEquals("Venda fechada", result);
        verify(vendaProdutos, never()).salvar(any(VendaProduto.class));
    }

    @Test
    public void testRemoveProdutoVendaAberta() {
        Venda venda = new Venda();
        venda.setSituacao(VendaSituacao.ABERTA);

        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);

        String result = vendaService.removeProduto(posicaoProd, codVenda);

        assertEquals("ok", result);
        verify(vendaProdutos, times(1)).removeProduto(posicaoProd);
    }

    @Test
    public void testRemoveProdutoVendaFechada() {
        Venda venda = new Venda();
        venda.setSituacao(VendaSituacao.FECHADA);

        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);

        String result = vendaService.removeProduto(posicaoProd, codVenda);

        assertEquals("Venda fechada", result);
        verify(vendaProdutos, never()).removeProduto(any());
    }

    @Test
    public void testRemoveProdutoComExcecao() {
        when(vendas.findByCodigoEquals(codVenda)).thenThrow(new RuntimeException("DB failure"));

        String result = vendaService.removeProduto(posicaoProd, codVenda);

        assertEquals("ok", result);
        verify(vendaProdutos, never()).removeProduto(any());
    }

    @Test
    public void testLista() {
        Venda venda1 = new Venda();
        venda1.setCodigo(1L);
        Venda venda2 = new Venda();
        venda2.setCodigo(2L);

        List<Venda> mockVendas = List.of(venda1, venda2);

        when(vendas.findAll()).thenReturn(mockVendas);

        List<Venda> result = vendaService.lista();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getCodigo());
        assertEquals(2L, result.get(1).getCodigo());

        verify(vendas, times(1)).findAll();
    }

    @Test
    public void testFechaVenda_Success() {
        Long vendaId = 1L;
        Long pagamentoTipoId = 100L;
        Double vlprodutos = 100.0;
        Double desconto = 0.0;
        Double acrescimo = 0.0;
        String[] vlParcelas = new String[] { "100.0" };
        String[] titulos = new String[] { "200" };

        Venda venda = new Venda();
        venda.setPessoa(new Pessoa());
        venda.setSituacao(VendaSituacao.ABERTA);
        when(vendas.findByCodigoEquals(vendaId)).thenReturn(venda);
        when(vendas.verificaSituacao(vendaId)).thenReturn("ABERTA");

        PagamentoTipo pagamentoTipo = new PagamentoTipo();
        pagamentoTipo.setFormaPagamento("00");
        when(formaPagamentos.busca(pagamentoTipoId)).thenReturn(pagamentoTipo);

        Titulo titulo = new Titulo();
        TituloTipo tipo = new TituloTipo();
        tipo.setSigla("DIN");
        titulo.setTipo(tipo);
        when(tituloService.busca(Long.valueOf(titulos[0]))).thenReturn(Optional.of(titulo));

        when(caixas.caixaIsAberto()).thenReturn(true);
        when(caixas.caixaAberto()).thenReturn(Optional.of(new Caixa()));
        when(usuarioService.buscaUsuario(any())).thenReturn(new Usuario());

        doNothing().when(receberServ).cadastrar(any(Receber.class));

        doNothing().when(vendas).fechaVenda(anyLong(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any());

        doNothing().when(produtos).movimentaEstoque(eq(vendaId), eq(EntradaSaida.SAIDA));

        String result = vendaService.fechaVenda(
                vendaId,
                pagamentoTipoId,
                vlprodutos,
                desconto,
                acrescimo,
                vlParcelas,
                titulos
        );

        assertEquals("Venda finalizada com sucesso", result);
    }

    @Test
    public void testFechaVenda_VendaFechada() {
        Venda venda = new Venda();
        venda.setSituacao(VendaSituacao.FECHADA);

        when(vendas.findByCodigoEquals(1L)).thenReturn(venda);

        assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(1L, 1L, 100.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"});
        }, "venda fechada");
    }

    @Test
    public void testFechaVenda_ValorZero() {
        when(vendas.findByCodigoEquals(1L)).thenReturn(new Venda());

        assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(1L, 1L, 0.0, 0.0, 0.0, new String[]{"0.0"}, new String[]{"1"});
        }, "Venda sem valor, verifique");
    }

    @Test
    public void testFechaVenda_SemCaixaAberto() {
        Venda venda = new Venda();
        venda.setCodigo(1L);
        venda.setSituacao(VendaSituacao.ABERTA);

        Pessoa pessoa = new Pessoa();
        pessoa.setCodigo(1L);
        venda.setPessoa(pessoa);

        PagamentoTipo pagamentoTipo = new PagamentoTipo();
        pagamentoTipo.setFormaPagamento("00");

        TituloTipo tituloTipo = new TituloTipo();
        tituloTipo.setSigla("DIN");

        Titulo titulo = new Titulo();
        titulo.setTipo(tituloTipo);

        when(vendas.findByCodigoEquals(1L)).thenReturn(venda);
        when(formaPagamentos.busca(1L)).thenReturn(pagamentoTipo);
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));
        when(caixas.caixaIsAberto()).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(
                    1L,
                    1L,
                    100.0,
                    0.0,
                    0.0,
                    new String[]{"100.0"},
                    new String[]{"1"}
            );
        });

        assertEquals("nenhum caixa aberto", ex.getMessage());
    }

    @Test
    public void testA_Prazo() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String[] vlParcelas = {"100.0"};
        String[] formaPagar = {"30"};
        Double vlProdutos = 100.0;
        Double acrescimo = 10.0;
        Double desconto = 5.0;
        int qtdVezes = 1;
        int sequencia = 1;
        int i = 0;

        Receber receber = new Receber();
        DataAtual dataAtual = mock(DataAtual.class);

        when(dataAtual.dataAtualTimeStamp()).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(dataAtual.DataAtualIncrementa(30)).thenReturn(LocalDate.now().plusDays(30).toString());

        Method method = VendaService.class.getDeclaredMethod("aprazo", Double.class, String[].class, DataAtual.class,
                String[].class, int.class, int.class, Receber.class, int.class, Double.class, Double.class);
        method.setAccessible(true);

        int novoSequencia = (int) method.invoke(vendaService, vlProdutos, vlParcelas, dataAtual, formaPagar,
                qtdVezes, sequencia, receber, i, acrescimo, desconto);

        assertEquals(sequencia + 1, novoSequencia);

        verify(parcelas).gerarParcela(
                eq(105.0),
                eq(0.0),
                eq(0.0),
                eq(0.0),
                eq(105.0),
                eq(receber),
                eq(0),
                eq(sequencia),
                any(Timestamp.class),
                any(java.sql.Date.class)
        );
    }

    @Test
    void successfulA_Vista_Test() throws Exception {
        String[] vlParcelas = {"100.0"};
        String[] formaPagar = {"0"};
        Double vlProdutos = 100.0;
        Double acrescimo = 10.0;
        Double desconto = 5.0;
        int qtdVezes = 1;
        int i = 0;

        Caixa caixa = new Caixa();
        when(caixas.caixaAberto()).thenReturn(Optional.of(caixa));

        Usuario usuario = new Usuario();
        Aplicacao instanciaMockada = mock(Aplicacao.class);
        when(instanciaMockada.getUsuarioAtual()).thenReturn("usuario1");
        when(usuarioService.buscaUsuario("usuario1")).thenReturn(usuario);

        ArgumentCaptor<CaixaLancamento> captor = ArgumentCaptor.forClass(CaixaLancamento.class);

        Method method = VendaService.class.getDeclaredMethod("avistaDinheiro", Double.class, String[].class, String[].class,
                int.class, int.class, Double.class, Double.class);
        method.setAccessible(true);

        int novaQtdVezes = (int) method.invoke(vendaService, vlProdutos, vlParcelas, formaPagar,
                qtdVezes, i, acrescimo, desconto);

        assertEquals(0, novaQtdVezes);

        verify(lancamentos).lancamento(captor.capture());

        CaixaLancamento lancamento = captor.getValue();
        assertEquals("Recebimento de venda á vista", lancamento.getObservacao());
        assertEquals(105.0, lancamento.getValor());
        assertEquals(TipoLancamento.RECEBIMENTO, lancamento.getTipo());
        assertEquals(EstiloLancamento.ENTRADA, lancamento.getEstilo());
        assertEquals(caixa, lancamento.getCaixa().get());
        assertEquals(usuario.getCodigo(), lancamento.getUsuario().getCodigo());
    }

    @Test
    void vendaAbertaTrueTest() throws Exception {
        Long codVenda = 123L;
        Venda venda = mock(Venda.class);
        when(venda.isAberta()).thenReturn(true);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);

        Method method = VendaService.class.getDeclaredMethod("vendaIsAberta", Long.class);
        method.setAccessible(true);

        Boolean resultado = (Boolean) method.invoke(vendaService, codVenda);

        assertTrue(resultado);
    }

    @Test
    void vendaAbertaFalseTest() throws Exception {
        Long codVenda = 456L;
        Venda venda = mock(Venda.class);
        when(venda.isAberta()).thenReturn(false);
        when(vendas.findByCodigoEquals(codVenda)).thenReturn(venda);

        Method method = VendaService.class.getDeclaredMethod("vendaIsAberta", Long.class);
        method.setAccessible(true);

        Boolean resultado = (Boolean) method.invoke(vendaService, codVenda);

        assertFalse(resultado);
    }

    @Test
    void testeQtdAbertos() {
        int qtdEsperada = 3;
        when(vendas.qtdVendasEmAberto()).thenReturn(qtdEsperada);

        int qtdRetornada = vendaService.qtdAbertos();

        assertEquals(qtdEsperada, qtdRetornada);
    }
}