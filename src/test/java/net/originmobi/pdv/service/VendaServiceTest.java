package net.originmobi.pdv.service;

import net.originmobi.pdv.controller.TituloService;
import net.originmobi.pdv.dto.PagamentoContext;
import net.originmobi.pdv.enumerado.EntradaSaida;
import net.originmobi.pdv.enumerado.VendaSituacao;
import net.originmobi.pdv.enumerado.caixa.EstiloLancamento;
import net.originmobi.pdv.enumerado.caixa.TipoLancamento;
import net.originmobi.pdv.exception.VendaException;
import net.originmobi.pdv.filter.VendaFilter;
import net.originmobi.pdv.model.*;
import net.originmobi.pdv.model.cartao.CartaoLancamento;
import net.originmobi.pdv.repository.VendaRepository;
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
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collections;
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
    private VendaRepository vendaRepository;

    @Mock
    private VendaProdutoService vendaProdutos;

    @Mock private PagamentoTipoService formaPagamentoService;
    @Mock private TituloService tituloService;
    @Mock private CaixaService caixaService;
    @Mock private ProdutoService produtos;
    @Mock private ReceberService receberServ;
    @Mock private CartaoLancamentoService cartaoLancamentoService;
    @Mock private CaixaLancamentoService lancamentoService;
    @Mock private ParcelaService parcelas;


    private final Long codVenda = 1L;
    private final Long codProduto = 10L;
    private final Double vlBalanca = 5.0;
    private final Long posicaoProd = 2L;

    @Mock
    private CartaoLancamento cartaoLancamento;


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
    void testAbreVendaAtualizaDadosVenda() {
        Venda vendaExistente = new Venda();
        vendaExistente.setCodigo(123L);
        vendaExistente.setPessoa(new Pessoa());
        vendaExistente.setObservacao("Observação");

        vendaService.abreVenda(vendaExistente);

        verify(vendaRepository).updateDadosVenda(
                eq(vendaExistente.getPessoa()),
                eq("Observação"),
                eq(123L)
        );

        verify(vendaRepository, never()).save(any());
    }

    @Test
    void testAbreVendaSalvaVendaDadosDefault() {
        Venda novaVenda = new Venda();
        when(usuarioService.buscaUsuario(any())).thenReturn(mock(Usuario.class));

        vendaService.abreVenda(novaVenda);

        assertNotNull(novaVenda.getData_cadastro());
        assertEquals(VendaSituacao.ABERTA, novaVenda.getSituacao());
        assertNotNull(novaVenda.getUsuario());
        assertEquals(0.0, novaVenda.getValor_produtos());

        verify(vendaRepository).save(novaVenda);
    }

    @Test
    void testAbreVendaWhenCodigoIsNull() {
        Venda venda = new Venda();
        venda.setCodigo(null);

        Aplicacao aplicacao = Aplicacao.getInstancia();

        Usuario usuarioMock = new Usuario();
        when(usuarioService.buscaUsuario(aplicacao.getUsuarioAtual())).thenReturn(usuarioMock);
        when(vendaRepository.save(any(Venda.class))).thenAnswer(invocation -> {
            Venda v = invocation.getArgument(0);
            v.setCodigo(123L);
            return v;
        });

        Long codigo = vendaService.abreVenda(venda);

        assertNotNull(codigo);
        assertEquals(123L, codigo);
        assertEquals(VendaSituacao.ABERTA, venda.getSituacao());
        assertEquals(usuarioMock, venda.getUsuario());
        assertEquals(0.0, venda.getValor_produtos());
        assertNotNull(venda.getData_cadastro());

        verify(vendaRepository).save(venda);
    }

    @Test
    void abreVendaErroSalvar() {
        Venda venda = mock(Venda.class);
        when(venda.getCodigo()).thenReturn(null);

        doThrow(new RuntimeException("erro")).when(vendaRepository).save(any(Venda.class));

        assertDoesNotThrow(() -> vendaService.abreVenda(venda));
    }


    @Test
    void abreVendaExceptionEmSave() {
        Venda venda = new Venda();
        venda.setCodigo(null);
        when(vendaRepository.save(any())).thenThrow(new RuntimeException("Erro"));

        assertDoesNotThrow(() -> vendaService.abreVenda(venda));
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

        when(vendaRepository.findByCodigo(eq(1234L), any(Pageable.class))).thenReturn(fakePage);

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

        when(vendaRepository.findBySituacaoEquals(eq(VendaSituacao.ABERTA), eq(pageable)))
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

        when(vendaRepository.findByCodigo(eq(1234L), any(Pageable.class))).thenReturn(null);

        assertNull(vendaService.busca(vendaFilter, situacao, pageable));
    }

    @Test
    public void testAddProdutoVendaAberta() {
        when(vendaRepository.verificaSituacao(codVenda)).thenReturn(VendaSituacao.ABERTA.toString());

        doNothing().when(vendaProdutos).salvar(any(VendaProduto.class));

        String result = vendaService.addProduto(codVenda, codProduto, vlBalanca);

        assertEquals("ok", result);
        verify(vendaProdutos, times(1)).salvar(any(VendaProduto.class));
    }

    @Test
    public void testAddProdutoVendaFechada() {
        when(vendaRepository.verificaSituacao(codVenda)).thenReturn(VendaSituacao.FECHADA.toString());

        String result = vendaService.addProduto(codVenda, codProduto, vlBalanca);

        assertEquals("Venda fechada", result);
        verify(vendaProdutos, never()).salvar(any(VendaProduto.class));
    }

    @Test
    void testAddProdutoException() {
        when(vendaRepository.verificaSituacao(codVenda)).thenReturn(VendaSituacao.ABERTA.toString());
        doThrow(new RuntimeException("Erro")).when(vendaProdutos).salvar(any());

        String result = vendaService.addProduto(codVenda, codProduto, vlBalanca);
        assertEquals("ok", result);
    }

    @Test
    public void testRemoveProdutoVendaAberta() {
        Venda venda = new Venda();
        venda.setSituacao(VendaSituacao.ABERTA);

        when(vendaRepository.findByCodigoEquals(codVenda)).thenReturn(venda);

        String result = vendaService.removeProduto(posicaoProd, codVenda);

        assertEquals("ok", result);
        verify(vendaProdutos, times(1)).removeProduto(posicaoProd);
    }

    @Test
    public void testRemoveProdutoVendaFechada() {
        Venda venda = new Venda();
        venda.setSituacao(VendaSituacao.FECHADA);

        when(vendaRepository.findByCodigoEquals(codVenda)).thenReturn(venda);

        String result = vendaService.removeProduto(posicaoProd, codVenda);

        assertEquals("Venda fechada", result);
        verify(vendaProdutos, never()).removeProduto(any());
    }

    @Test
    public void testRemoveProdutoSituacaoNull() {
        Venda venda = new Venda();
        venda.setSituacao(null);

        when(vendaRepository.findByCodigoEquals(codVenda)).thenReturn(venda);

        String result = vendaService.removeProduto(posicaoProd, codVenda);

        assertEquals("ok", result);
        verify(vendaProdutos, never()).removeProduto(any());
    }

    @Test
    public void testRemoveProdutoComExcecao() {
        when(vendaRepository.findByCodigoEquals(codVenda)).thenThrow(new RuntimeException("DB failure"));

        String result = vendaService.removeProduto(posicaoProd, codVenda);

        assertEquals("ok", result);
        verify(vendaProdutos, never()).removeProduto(any());
    }

    @Test
    void removeProdutoFindException() {
        when(vendaRepository.findByCodigoEquals(codVenda)).thenThrow(new RuntimeException("erro"));

        String result = vendaService.removeProduto(posicaoProd, codVenda);
        assertEquals("ok", result);
    }

    @Test
    public void testLista() {
        Venda venda1 = new Venda();
        venda1.setCodigo(1L);
        Venda venda2 = new Venda();
        venda2.setCodigo(2L);

        List<Venda> mockVendas = List.of(venda1, venda2);

        when(vendaRepository.findAll()).thenReturn(mockVendas);

        List<Venda> result = vendaService.lista();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getCodigo());
        assertEquals(2L, result.get(1).getCodigo());

        verify(vendaRepository, times(1)).findAll();
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
        when(vendaRepository.findByCodigoEquals(vendaId)).thenReturn(venda);
        when(vendaRepository.verificaSituacao(vendaId)).thenReturn("ABERTA");

        PagamentoTipo pagamentoTipo = new PagamentoTipo();
        pagamentoTipo.setFormaPagamento("00");
        when(formaPagamentoService.busca(pagamentoTipoId)).thenReturn(pagamentoTipo);

        Titulo titulo = new Titulo();
        TituloTipo tipo = new TituloTipo();
        tipo.setSigla("DIN");
        titulo.setTipo(tipo);
        when(tituloService.busca(Long.valueOf(titulos[0]))).thenReturn(Optional.of(titulo));

        when(caixaService.caixaIsAberto()).thenReturn(true);
        when(caixaService.caixaAberto()).thenReturn(Optional.of(new Caixa()));
        when(usuarioService.buscaUsuario(any())).thenReturn(new Usuario());

        doNothing().when(receberServ).cadastrar(any(Receber.class));

        doNothing().when(vendaRepository).fechaVenda(anyLong(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any());

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

        when(vendaRepository.findByCodigoEquals(1L)).thenReturn(venda);

        assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(1L, 1L, 100.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"});
        }, "venda fechada");
    }

    @Test
    public void testFechaVenda_ValorZero() {
        when(vendaRepository.findByCodigoEquals(1L)).thenReturn(new Venda());

        assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(1L, 1L, 0.0, 0.0, 0.0, new String[]{"0.0"}, new String[]{"1"});
        }, "Venda sem valor, verifique");
    }

    @Test
    public void testFechaVendaSemValorParcelas() {
        Venda venda = new Venda();
        venda.setValor_produtos(-100.0);
        when(vendaRepository.findByCodigoEquals(any())).thenReturn(venda);

        assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(1L, 1L, 0.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"});
        }, "Venda sem valor, verifique");
    }

    @Test
    public void testFechaVendaValorNegativo() {
        Venda venda = new Venda();
        venda.setValor_produtos(-100.0);
        venda.setCodigo(1L);
        venda.setSituacao(VendaSituacao.ABERTA);
        when(vendaRepository.findByCodigoEquals(any())).thenReturn(venda);

        assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(1L, 1L, -100.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"});
        }, "Venda sem valor, verifique");
    }


    @Test
    void fechaVendaPessoaNula() {
        // Arrange
        Long vendaId = 1L;
        Long pagamentoTipoId = 2L;

        Venda venda = new Venda();
        venda.setCodigo(vendaId);
        venda.setSituacao(VendaSituacao.ABERTA);
        venda.setPessoa(null);

        when(vendaRepository.findByCodigoEquals(vendaId)).thenReturn(venda);
        when(formaPagamentoService.busca(pagamentoTipoId)).thenReturn(new PagamentoTipo("A","Crédito", new Date(10000L)));
        when(tituloService.busca(anyLong())).thenReturn(Optional.of(mock(Titulo.class)));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                vendaService.fechaVenda(
                        vendaId,
                        pagamentoTipoId,
                        100.0,
                        0.0,
                        0.0,
                        new String[]{"100.0"},
                        new String[]{"1"}
                )
        );

        assertEquals("Venda sem cliente, verifique", ex.getMessage());
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

        when(vendaRepository.findByCodigoEquals(1L)).thenReturn(venda);
        when(formaPagamentoService.busca(1L)).thenReturn(pagamentoTipo);
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));
        when(caixaService.caixaIsAberto()).thenReturn(false);

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

        assertEquals("Nenhum caixa aberto", ex.getMessage());
    }

    @Test
    void fechaVenda_CartaoCredito() {
        // Setup
        Venda venda = new Venda();
        venda.setPessoa(new Pessoa());
        venda.setSituacao(VendaSituacao.ABERTA);

        when(vendaRepository.verificaSituacao(1L)).thenReturn("ABERTA");
        when(vendaRepository.findByCodigoEquals(1L)).thenReturn(venda);

        PagamentoTipo pagamentoTipo = new PagamentoTipo();
        pagamentoTipo.setFormaPagamento("00");

        Titulo titulo = new Titulo();
        TituloTipo tipo = new TituloTipo();
        tipo.setSigla("CARTCRED");
        titulo.setTipo(tipo);

        when(formaPagamentoService.busca(1L)).thenReturn(pagamentoTipo);
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));

        doNothing().when(receberServ).cadastrar(any());
        doNothing().when(cartaoLancamentoService).lancamento(anyDouble(), any());
        doNothing().when(vendaRepository).fechaVenda(anyLong(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any());
        doNothing().when(produtos).movimentaEstoque(anyLong(), any());

        String result = vendaService.fechaVenda(
                1L, 1L, 100.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"}
        );

        assertEquals("Venda finalizada com sucesso", result);
    }

    @Test
    void fechaVenda_VendaAPrazoSemCliente() {
        Venda venda = new Venda();
        venda.setSituacao(VendaSituacao.ABERTA);
        venda.setPessoa(null); // importante

        PagamentoTipo pagamentoTipo = new PagamentoTipo();
        pagamentoTipo.setFormaPagamento("30");

        Titulo titulo = new Titulo();
        TituloTipo tipo = new TituloTipo();
        tipo.setSigla("DIN");
        titulo.setTipo(tipo);

        when(vendaRepository.verificaSituacao(1L)).thenReturn("ABERTA");
        when(vendaRepository.findByCodigoEquals(1L)).thenReturn(venda);
        when(formaPagamentoService.busca(1L)).thenReturn(pagamentoTipo);
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(1L, 1L, 100.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"});
        });

        assertEquals("Venda sem cliente, verifique", ex.getMessage());
    }

    @Test
    void fechaVenda_TituloNaoEncontrado() {
        Venda venda = new Venda();
        venda.setPessoa(new Pessoa());
        venda.setSituacao(VendaSituacao.ABERTA);

        when(vendaRepository.verificaSituacao(1L)).thenReturn("ABERTA");
        when(vendaRepository.findByCodigoEquals(1L)).thenReturn(venda);

        PagamentoTipo pagamentoTipo = new PagamentoTipo();
        pagamentoTipo.setFormaPagamento("00");

        when(formaPagamentoService.busca(1L)).thenReturn(pagamentoTipo);
        when(tituloService.busca(1L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            vendaService.fechaVenda(1L, 1L, 100.0, 0.0, 0.0, new String[]{"100.0"}, new String[]{"1"});
        });
    }

    @Test
    void testA_Prazo() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // Setup values
        String[] vlParcelas = {"100.0"};
        String[] formaPagar = {"30"};
        Double vlProdutos = 100.0;
        Double acrescimo = 10.0;
        Double desconto = 5.0;
        int qtdVezes = 1;
        int sequencia = 1;
        int i = 0;

        Receber receber = new Receber();
        Venda venda = new Venda(); // Optional: fill with more data if aprazo uses it
        DataAtual dataAtual = mock(DataAtual.class);

        Timestamp timestampAtual = new Timestamp(System.currentTimeMillis());
        LocalDate vencimento = LocalDate.now().plusDays(30);

        // Mock expected dates
        when(dataAtual.dataAtualTimeStamp()).thenReturn(timestampAtual);
        when(dataAtual.DataAtualIncrementa(30)).thenReturn(vencimento.toString());

        // Build context
        PagamentoContext context = new PagamentoContext(
                formaPagar,
                new String[] {"Título 1"},
                vlProdutos,
                vlParcelas,
                desconto,
                acrescimo,
                venda,
                dataAtual,
                receber
        );

        // Invoke private method with reflection
        Method method = VendaService.class.getDeclaredMethod("aprazo", PagamentoContext.class, int.class, int.class);
        method.setAccessible(true);

        int novoSequencia = (int) method.invoke(vendaService, context, sequencia, i);

        // Assertion
        assertEquals(sequencia + 1, novoSequencia);

        // Verify parcelas.gerarParcela called correctly
        verify(parcelas).gerarParcela(
                eq(105.0),      // valor parcela final (100 + 10 - 5)
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
        when(caixaService.caixaAberto()).thenReturn(Optional.of(caixa));

        Usuario usuario = new Usuario();
        Aplicacao instanciaMockada = mock(Aplicacao.class);
        when(instanciaMockada.getUsuarioAtual()).thenReturn("usuario1");
        when(usuarioService.buscaUsuario("usuario1")).thenReturn(usuario);

        ArgumentCaptor<CaixaLancamento> captor = ArgumentCaptor.forClass(CaixaLancamento.class);

        Method method = VendaService.class.getDeclaredMethod("avistaDinheiro", Double.class, String[].class, int.class,
                int.class, Double.class, Double.class);
        method.setAccessible(true);

        int novaQtdVezes = (int) method.invoke(vendaService, vlProdutos, vlParcelas,
                qtdVezes, i, acrescimo, desconto);

        assertEquals(0, novaQtdVezes);

        verify(lancamentoService).lancamento(captor.capture());

        CaixaLancamento lancamento = captor.getValue();
        assertEquals("Recebimento de venda á vista", lancamento.getObservacao());
        assertEquals(105.0, lancamento.getValor());
        assertEquals(TipoLancamento.RECEBIMENTO, lancamento.getTipo());
        assertEquals(EstiloLancamento.ENTRADA, lancamento.getEstilo());
        assertEquals(caixa, lancamento.getCaixa().get());
        assertEquals(usuario.getCodigo(), lancamento.getUsuario().getCodigo());
    }

    @Test
    void fechaVenda_calculaValorTotalCorretamente() {
        PagamentoTipo pagamentoTipo = new PagamentoTipo();
        pagamentoTipo.setFormaPagamento("30");

        when(vendaRepository.findByCodigoEquals(anyLong())).thenReturn(new Venda());
        when(formaPagamentoService.busca(anyLong())).thenReturn(pagamentoTipo);
        doNothing().when(formaPagamentoService).cadastrar(any());

        Venda venda = new Venda();
        venda.setSituacao(VendaSituacao.ABERTA);
        when(vendaRepository.findByCodigoEquals(anyLong())).thenReturn(venda);

        vendaService.fechaVenda(2L, 1L, 100.0, 10.0, 20.0, new String[]{"110"}, new String[]{"1"});

        // Verify correct value (100 + 20 - 10 = 110)
        ArgumentCaptor<Receber> receberCaptor = ArgumentCaptor.forClass(Receber.class);
        verify(receberServ).cadastrar(receberCaptor.capture());
        assertEquals(110.0, receberCaptor.getValue().getValor_total(), 0.01);
    }

    @Test
    void avistaDinheiro_valorDivergente() throws Exception {
        Method method = VendaService.class.getDeclaredMethod("avistaDinheiro", Double.class, String[].class,
                int.class, int.class, Double.class, Double.class);
        method.setAccessible(true);

        String[] vlParcelas = {"50.0", "40.0"}; // total = 90.0
        String[] formaPagar = {"00", "00"};
        Double vlprodutos = 100.0; // diferente do totalParcelas
        Double acrescimo = 0.0;
        Double desconto = 0.0;

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(vendaService, vlprodutos, vlParcelas, 2, 0, acrescimo, desconto);
        });

        Throwable realException = exception.getCause();

        assertTrue(realException instanceof RuntimeException);
        assertTrue(realException.getMessage().contains("Valor das parcelas diferente"));
    }

    @Test
    void testeAvistaDinheiroValorParcelasDiferente() {
        Long codigoVenda = 1L;
        Long codigoTipoPagamento = 2L;
        Double valorProdutos = 100.0;
        Double desconto = 0.0;
        Double acrescimo = 0.0;
        String[] valoresParcelas = {"50.0"};
        String[] codigosTitulos = {"1"};

        Venda venda = new Venda();
        venda.setCodigo(codigoVenda);
        venda.setSituacao(VendaSituacao.ABERTA);
        venda.setValor_produtos(valorProdutos);
        Pessoa pessoa = new Pessoa();
        venda.setPessoa(pessoa);

        PagamentoTipo tipoPagamento = new PagamentoTipo();
        tipoPagamento.setCodigo(codigoTipoPagamento);
        tipoPagamento.setFormaPagamento("00");

        Titulo titulo = new Titulo();
        titulo.setCodigo(1L);
        TituloTipo tipoTitulo = new TituloTipo();
        tipoTitulo.setSigla("DIN");
        tipoTitulo.setDescricao("Dinheiro");
        titulo.setTipo(tipoTitulo);

        when(vendaRepository.findByCodigoEquals(codigoVenda)).thenReturn(venda);
        when(formaPagamentoService.busca(codigoTipoPagamento)).thenReturn(tipoPagamento);
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));
        when(caixaService.caixaIsAberto()).thenReturn(true);
        when(caixaService.caixaAberto()).thenReturn(Optional.of(new Caixa()));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                vendaService.fechaVenda(codigoVenda, codigoTipoPagamento, valorProdutos, desconto, acrescimo, valoresParcelas, codigosTitulos)
        );
        assertEquals("Valor das parcelas diferente do valor total de produtos, verifique", ex.getMessage());
        verify(lancamentoService, never()).lancamento(any());
    }

    @Test
    void testeAVistaDinheiroExceptionLancamento() {
        Long codigoVenda = 1L;
        Double valorProdutos = 100.0;
        String[] valoresParcelas = {"100.0"};
        String[] codigosTitulos = {"1"};
        Double desconto = 0.0;
        Double acrescimo = 0.0;
        Long codigoTipoPagamento = 2L;

        Venda venda = new Venda();
        venda.setCodigo(codigoVenda);
        venda.setSituacao(VendaSituacao.ABERTA);
        venda.setValor_produtos(valorProdutos);
        Pessoa pessoa = new Pessoa();
        venda.setPessoa(pessoa);

        PagamentoTipo tipoPagamento = new PagamentoTipo();
        tipoPagamento.setCodigo(codigoTipoPagamento);
        tipoPagamento.setFormaPagamento("00");

        Titulo titulo = new Titulo();
        titulo.setCodigo(1L);
        TituloTipo tipoTitulo = new TituloTipo();
        tipoTitulo.setSigla("DIN");
        tipoTitulo.setDescricao("Dinheiro");
        titulo.setTipo(tipoTitulo);

        Usuario usuario = new Usuario();
        usuario.setCodigo(1L);
        Caixa caixa = new Caixa();
        caixa.setCodigo(1L);

        when(vendaRepository.findByCodigoEquals(codigoVenda)).thenReturn(venda);
        when(formaPagamentoService.busca(codigoTipoPagamento)).thenReturn(tipoPagamento);
        when(tituloService.busca(1L)).thenReturn(Optional.of(titulo));
        when(usuarioService.buscaUsuario("gerente")).thenReturn(usuario);
        when(caixaService.caixaAberto()).thenReturn(Optional.of(caixa));
        when(caixaService.caixaIsAberto()).thenReturn(true);
        doThrow(new VendaException("Erro ao fechar a venda, chame o suporte"))
                .when(lancamentoService).lancamento(any());
        VendaException ex = assertThrows(VendaException.class, () ->
                vendaService.fechaVenda(codigoVenda, codigoTipoPagamento, valorProdutos, desconto, acrescimo, valoresParcelas, codigosTitulos)
        );
        assertEquals("Erro ao fechar a venda, chame o suporte", ex.getMessage());
    }

    @Test
    void avistaDinheiro_parcelaVazia() throws Exception {
        Method method = VendaService.class.getDeclaredMethod("avistaDinheiro", Double.class, String[].class,
                int.class, int.class, Double.class, Double.class);
        method.setAccessible(true);

        String[] parcelas = {""};
        String[] forma = {"00"};

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(vendaService, 100.0, parcelas, 1, 0, 0.0, 0.0);
        });

        Throwable realException = exception.getCause();

        assertTrue(realException instanceof RuntimeException);
        assertTrue(realException.getMessage().contains("Parcela sem valor"));
    }

    @Test
    void vendaAbertaTrueTest() throws Exception {
        Long codVenda = 123L;
        Venda venda = mock(Venda.class);
        when(venda.isAberta()).thenReturn(true);
        when(vendaRepository.findByCodigoEquals(codVenda)).thenReturn(venda);

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
        when(vendaRepository.findByCodigoEquals(codVenda)).thenReturn(venda);

        Method method = VendaService.class.getDeclaredMethod("vendaIsAberta", Long.class);
        method.setAccessible(true);

        Boolean resultado = (Boolean) method.invoke(vendaService, codVenda);

        assertFalse(resultado);
    }

    @Test
    void testeQtdAbertosSemVendasAbertas() {
        int quantidadeEsperada = 0;
        when(vendaRepository.qtdVendasEmAberto()).thenReturn(quantidadeEsperada);

        int resultado = vendaService.qtdAbertos();

        assertEquals(quantidadeEsperada, resultado);
        verify(vendaRepository).qtdVendasEmAberto();
    }

    @Test
    void testeQtdAbertos() {
        int qtdEsperada = 3;
        when(vendaRepository.qtdVendasEmAberto()).thenReturn(qtdEsperada);

        int qtdRetornada = vendaService.qtdAbertos();

        assertEquals(qtdEsperada, qtdRetornada);
    }
}