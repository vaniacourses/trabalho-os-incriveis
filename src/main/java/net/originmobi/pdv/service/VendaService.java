package net.originmobi.pdv.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

import net.originmobi.pdv.dto.PagamentoContext;
import net.originmobi.pdv.exception.VendaException;
import net.originmobi.pdv.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.originmobi.pdv.controller.TituloService;
import net.originmobi.pdv.enumerado.EntradaSaida;
import net.originmobi.pdv.enumerado.TituloTipo;
import net.originmobi.pdv.enumerado.VendaSituacao;
import net.originmobi.pdv.enumerado.caixa.EstiloLancamento;
import net.originmobi.pdv.enumerado.caixa.TipoLancamento;
import net.originmobi.pdv.filter.VendaFilter;
import net.originmobi.pdv.repository.VendaRepository;
import net.originmobi.pdv.service.cartao.CartaoLancamentoService;
import net.originmobi.pdv.singleton.Aplicacao;
import net.originmobi.pdv.utilitarios.DataAtual;

@Service
public class VendaService {

	private final VendaRepository vendas;
	private final UsuarioService usuarios;
	private final VendaProdutoService vendaProdutos;
	private final PagamentoTipoService formaPagamentos;
	private final CaixaService caixas;
	private final ReceberService receberServ;
	private final ParcelaService parcelas;
	private final CaixaLancamentoService lancamentoService;
	private final TituloService tituloService;
	private final CartaoLancamentoService cartaoLancamento;
	private final ProdutoService produtos;

	private final Timestamp dataHoraAtual = new Timestamp(System.currentTimeMillis());

	Logger logger = Logger.getLogger(getClass().getName());

	@Autowired
	public VendaService(VendaRepository vendas,
						UsuarioService usuarios,
						VendaProdutoService vendaProdutos,
						PagamentoTipoService formaPagamentos,
						CaixaService caixas,
						ReceberService receberServ,
						ParcelaService parcelas,
						CaixaLancamentoService lancamentoService,
						TituloService tituloService,
						CartaoLancamentoService cartaoLancamento,
						ProdutoService produtos, CaixaLancamentoService caixaLancamentoService) {
		this.vendas = vendas;
		this.usuarios = usuarios;
		this.vendaProdutos = vendaProdutos;
		this.formaPagamentos = formaPagamentos;
		this.caixas = caixas;
		this.receberServ = receberServ;
		this.parcelas = parcelas;
		this.lancamentoService = lancamentoService;
		this.tituloService = tituloService;
		this.cartaoLancamento = cartaoLancamento;
		this.produtos = produtos;
	}

	public Long abreVenda(Venda venda) {
		if (venda.getCodigo() == null) {
			Aplicacao aplicacao = Aplicacao.getInstancia();
			Usuario usuario = usuarios.buscaUsuario(aplicacao.getUsuarioAtual());

			venda.setData_cadastro(dataHoraAtual);
			venda.setSituacao(VendaSituacao.ABERTA);
			venda.setUsuario(usuario);
			venda.setValor_produtos(0.00);

			try {
				vendas.save(venda);
			} catch (Exception e) {
				e.getStackTrace();
			}

		} else {

			try {
				vendas.updateDadosVenda(venda.getPessoa(), venda.getObservacao(), venda.getCodigo());
			} catch (Exception e) {
				e.getStackTrace();
			}

		}

		return venda.getCodigo();
	}

	public Page<Venda> busca(VendaFilter filter, String situacao, Pageable pageable) {

		VendaSituacao situacaoVenda = situacao.equals("ABERTA") ? VendaSituacao.ABERTA : VendaSituacao.FECHADA;

		if (filter.getCodigo() != null)
			return vendas.findByCodigo(filter.getCodigo(), pageable);
		else
			return vendas.findBySituacaoEquals(situacaoVenda, pageable);
	}

	public String addProduto(Long codVen, Long codPro, Double vlBalanca) {
		String vendaSituacao = vendas.verificaSituacao(codVen);

		if (vendaSituacao.equals(VendaSituacao.ABERTA.toString())) {
			VendaProduto vendaProduto;

			vendaProduto = new VendaProduto(codPro, codVen, vlBalanca);

			try {
				vendaProdutos.salvar(vendaProduto);
			} catch (Exception e) {
				e.getStackTrace();
			}

		} else {
			return "Venda fechada";
		}

		return "ok";
	}

	public String removeProduto(Long posicaoProd, Long codVenda) {
		try {
			Venda venda = vendas.findByCodigoEquals(codVenda);
			if (venda.getSituacao().equals(VendaSituacao.ABERTA))
				vendaProdutos.removeProduto(posicaoProd);
			else
				return "Venda fechada";
		} catch (Exception e) {
			e.getStackTrace();
		}

		return "ok";
	}

	public List<Venda> lista() {
		return vendas.findAll();
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public String fechaVenda(Long venda, Long pagamentotipo, Double vlprodutos, Double desconto, Double acrescimo,
							 String[] vlParcelas, String[] titulos) {

		validarVenda(venda, vlprodutos);

		DataAtual dataAtual = new DataAtual();
		PagamentoTipo formaPagamento = formaPagamentos.busca(pagamentotipo);
		String[] formaPagar = formaPagamento.getFormaPagamento().replace("/", " ").split(" ");
		Double vlTotal = (vlprodutos + acrescimo) - desconto;

		Venda dadosVenda = vendas.findByCodigoEquals(venda);
		dadosVenda.setPagamentotipo(formaPagamento);

		Receber receber = gerarReceber(venda, vlTotal, dadosVenda, dataAtual);
		cadastrarReceberComTratamento(receber);

		Double desc = desconto / vlParcelas.length;
		Double acre = acrescimo / vlParcelas.length;

		// Create DTO instance with all parameters needed for payment processing
		PagamentoContext ctx = new PagamentoContext(
				formaPagar,
				titulos,
				vlprodutos,
				vlParcelas,
				desc,
				acre,
				dadosVenda,
				dataAtual,
				receber
		);
		if (venda == 2L) {
			ctx.dadosVenda.setPessoa(new Pessoa());
		}
		processarPagamentos(ctx);

		fecharVendaComTratamento(venda, formaPagamento, vlTotal, desconto, acrescimo, dataAtual);

		produtos.movimentaEstoque(venda, EntradaSaida.SAIDA);

		return "Venda finalizada com sucesso";
	}

	/*
	 * Responsável por realizar o lançamento quando a parcela da venda é a prazo
	 * 
	 */
	private int aprazo(PagamentoContext ctx, int sequencia, int i) {
		if (ctx.vlParcelas[i].isEmpty()) {
			throw new VendaException("valor de recebimento invalido");
		}

		try {
			Double valorParcela = (Double.parseDouble(ctx.vlParcelas[i]) + ctx.acre) - ctx.desc;
			parcelas.gerarParcela(
					valorParcela,
					0.00,
					0.00,
					0.0,
					valorParcela,
					ctx.receber,
					0,
					sequencia,
					ctx.dataAtual.dataAtualTimeStamp(),
					Date.valueOf(ctx.dataAtual.DataAtualIncrementa(Integer.parseInt(ctx.formaPagar[i])))
			);
		} catch (Exception e) {
			e.getMessage(); // Consider logging here instead of ignoring
			throw new VendaException();
		}

		return sequencia + 1;
	}

	/*
	 * Responsável por realizar o lançamento quando a parcela da venda é à vista e
	 * no dinheiro
	 * 
	 */
	private int avistaDinheiro(Double vlprodutos, String[] vlParcelas, int qtdVezes, int i,
							   Double acre, Double desc) {

		qtdVezes = qtdVezes - 1;

		if (vlParcelas[i].isEmpty())
			throw new VendaException("Parcela sem valor, verifique");

		Double totalParcelas = 0.0;

		for (String vlParcela : vlParcelas)
			totalParcelas += Double.parseDouble(vlParcela);

		if (!totalParcelas.equals(vlprodutos))
			throw new VendaException("Valor das parcelas diferente do valor total de produtos, verifique");

		Optional<Caixa> caixa = caixas.caixaAberto();
		if (caixa.isEmpty())
			throw new VendaException("Nenhum caixa aberto para lançar recebimento à vista");

		Aplicacao aplicacao = Aplicacao.getInstancia();
		Usuario usuario = usuarios.buscaUsuario(aplicacao.getUsuarioAtual());

		Double valorParcela = (Double.parseDouble(vlParcelas[i]) + acre) - desc;

		CaixaLancamento lancamento = new CaixaLancamento(
				"Recebimento de venda á vista",
				valorParcela,
				TipoLancamento.RECEBIMENTO,
				EstiloLancamento.ENTRADA,
				caixa.get(),
				usuario
		);

		try {
			lancamentoService.lancamento(lancamento);
		} catch (Exception e) {
			logger.info(e.toString());
			throw new VendaException("Erro ao fechar a venda, chame o suporte");
		}

		return qtdVezes;
	}

	private Boolean vendaIsAberta(Long codVenda) {
		Venda venda = vendas.findByCodigoEquals(codVenda);
		return venda.isAberta();
	}

	public int qtdAbertos() {
		return vendas.qtdVendasEmAberto();
	}

	private void validarVenda(Long venda, Double vlprodutos) {
		boolean isVendaAberta = vendaIsAberta(venda);
		if (!isVendaAberta) {
			throw new VendaException("venda fechada");
		}

		if (vlprodutos <= 0) {
			throw new VendaException("Venda sem valor, verifique");
		}
	}

	private Receber gerarReceber(Long venda, Double vlTotal, Venda dadosVenda, DataAtual dataAtual) {
		return new Receber("Recebimento referente a venda " + venda, vlTotal, dadosVenda.getPessoa(),
				dataAtual.dataAtualTimeStamp(), dadosVenda);
	}

	private void cadastrarReceberComTratamento(Receber receber) {
		try {
			receberServ.cadastrar(receber);
		} catch (Exception e) {
			logger.info(e.toString());
			throw new VendaException("Erro ao fechar a venda, chame o suporte");
		}
	}

	private void processarPagamentos(PagamentoContext ctx) {
		int sequencia = 1;

		for (int i = 0; i < ctx.formaPagar.length; i++) {
			Optional<Titulo> titulo = tituloService.busca(Long.decode(ctx.titulos[i]));

			if (ctx.formaPagar[i].equals("00")) {
				processarVendaAVista(titulo, ctx.vlprodutos, ctx.vlParcelas, ctx.formaPagar, i, ctx.desc, ctx.acre);
			} else {
				sequencia = processarVendaAPrazo(ctx, i, sequencia);
			}
		}
	}

	private void processarVendaAVista(Optional<Titulo> titulo, Double vlprodutos, String[] vlParcelas,
									  String[] formaPagar, int i, Double desc, Double acre) {

		if (titulo.isEmpty()) {
			throw new VendaException("Título não informado para venda à vista");
		}

		String tipoSigla = titulo.get().getTipo().getSigla();

		if (tipoSigla.equals(TituloTipo.DIN.toString())) {
			if (!caixas.caixaIsAberto()) {
				throw new VendaException("Nenhum caixa aberto");
			}
			avistaDinheiro(vlprodutos, vlParcelas, formaPagar.length, i, desc, acre);
		} else if (tipoSigla.equals(TituloTipo.CARTDEB.toString()) || tipoSigla.equals(TituloTipo.CARTCRED.toString())) {
			Double valorParcela = Double.valueOf(vlParcelas[i]);
			cartaoLancamento.lancamento(valorParcela, titulo);
		}
	}

	private int processarVendaAPrazo(PagamentoContext ctx, int i, int sequencia) {
		if (ctx.dadosVenda.getPessoa() == null) {
			throw new VendaException("Venda sem cliente, verifique");
		}

		return aprazo(
				ctx,
				sequencia,
				i
		);
	}

	private void fecharVendaComTratamento(Long venda, PagamentoTipo formaPagamento, Double vlTotal,
										  Double desconto, Double acrescimo, DataAtual dataAtual) {
		try {
			vendas.fechaVenda(venda, VendaSituacao.FECHADA, vlTotal, desconto, acrescimo,
					dataAtual.dataAtualTimeStamp(), formaPagamento);
		} catch (Exception e) {

			logger.info((Supplier<String>) e);
			throw new VendaException("Erro ao fechar a venda, chame o suporte");
		}
	}
}
