package net.originmobi.pdv.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import net.originmobi.pdv.controller.TituloService;
import net.originmobi.pdv.enumerado.TituloTipo;
import net.originmobi.pdv.enumerado.caixa.EstiloLancamento;
import net.originmobi.pdv.enumerado.caixa.TipoLancamento;
import net.originmobi.pdv.exception.RecebimentoNotFoundException;
import net.originmobi.pdv.exception.TituloNotFoundException;
import net.originmobi.pdv.exception.ClienteNotFoundException;
import net.originmobi.pdv.exception.LancamentoNotFoundException;
import net.originmobi.pdv.exception.ParcelaDeOutroClienteException;
import net.originmobi.pdv.exception.ParcelaQuitadaException;
import net.originmobi.pdv.exception.RecebimentoInvalidoException;
import net.originmobi.pdv.model.Caixa;
import net.originmobi.pdv.model.CaixaLancamento;
import net.originmobi.pdv.model.Parcela;
import net.originmobi.pdv.model.Pessoa;
import net.originmobi.pdv.model.Recebimento;
import net.originmobi.pdv.model.Titulo;
import net.originmobi.pdv.model.Usuario;
import net.originmobi.pdv.repository.RecebimentoRepository;
import net.originmobi.pdv.service.cartao.CartaoLancamentoService;
import net.originmobi.pdv.singleton.Aplicacao;
import net.originmobi.pdv.utilitarios.DataAtual;

@Service
public class RecebimentoService {

	private final RecebimentoRepository recebimentos;
	private final PessoaService pessoas;
	private final RecebimentoParcelaService receParcelas;
	private final ParcelaService parcelas;
	private final CaixaService caixas;
	private final UsuarioService usuarios;
	private final CaixaLancamentoService lancamentos;
	private final TituloService titulos;
	private final CartaoLancamentoService cartaoLancamentos;

	public RecebimentoService(
			RecebimentoRepository recebimentos,
			PessoaService pessoas,
			RecebimentoParcelaService receParcelas,
			ParcelaService parcelas,
			CaixaService caixas,
			UsuarioService usuarios,
			CaixaLancamentoService lancamentos,
			TituloService titulos,
			CartaoLancamentoService cartaoLancamentos) {

		this.recebimentos = recebimentos;
		this.pessoas = pessoas;
		this.receParcelas = receParcelas;
		this.parcelas = parcelas;
		this.caixas = caixas;
		this.usuarios = usuarios;
		this.lancamentos = lancamentos;
		this.titulos = titulos;
		this.cartaoLancamentos = cartaoLancamentos;
	}

	public String abrirRecebimento(Long codpes, String[] arrayParcelas) {
		List<Parcela> lista = new ArrayList<>();

		DataAtual dataAtual = new DataAtual();
		Double vlTotal = 0.0;

		for (int i = 0; i < arrayParcelas.length; i++) {
			Parcela parcela = parcelas.busca(Long.decode(arrayParcelas[i]));

			if (parcela.getQuitado() == 1)
				throw new ParcelaQuitadaException("Parcela " + parcela.getCodigo() + " já esta quitada, verifique.");

			if (!codpes.equals(parcela.getReceber().getPessoa().getCodigo()))
			    throw new ParcelaDeOutroClienteException("A parcela " + parcela.getCodigo() + " não pertence ao cliente selecionado");

			try {
				lista.add(parcela);

				vlTotal = vlTotal + parcela.getValor_restante();

			} catch (Exception e) {
				e.getMessage();
				throw new RecebimentoNotFoundException("Erro ao receber, chame o suporte");
			}
		}

		Optional<Pessoa> pessoa = pessoas.buscaPessoa(codpes);

		if (!pessoa.isPresent())
			throw new ClienteNotFoundException("Cliente não encontrado");

		Recebimento recebimento = new Recebimento(vlTotal, dataAtual.dataAtualTimeStamp(), pessoa.get(), lista);

		try {
			recebimentos.save(recebimento);
		} catch (Exception e) {
			e.getMessage();
			throw new RecebimentoNotFoundException("Erro ao receber, chame o suporte");
		}

		return recebimento.getCodigo().toString();
	}

	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	public String receber(Long codreceber, Double vlrecebido, Double vlacrescimo, Double vldesconto, Long codtitulo) {

	    // Validações iniciais
	    if (codtitulo == null || codtitulo == 0)
	        throw new TituloNotFoundException("Selecione um título para realizar o recebimento");

	    Recebimento recebimento = recebimentos.findById(codreceber)
	        .orElseThrow(() -> new RecebimentoNotFoundException("Recebimento não encontrado"));

	    if (recebimento.getData_processamento() != null)
	        throw new RecebimentoNotFoundException("Recebimento já está fechado");

	    Titulo tituloEntity = titulos.busca(codtitulo)
	        .orElseThrow(() -> new TituloNotFoundException("Título não encontrado"));

	    recebimento.setTitulo(tituloEntity);

	    Double vlrecebimento = arredonda(recebimento.getValor_total());

	    if (vlrecebido > vlrecebimento)
	        throw new RecebimentoInvalidoException("Valor de recebimento é superior aos títulos");

	    if (vlrecebido <= 0.0)
	        throw new RecebimentoNotFoundException("Valor de recebimento inválido");

	    List<Parcela> parcelasRecebimento = receParcelas.parcelasDoReceber(codreceber);

	    if (parcelasRecebimento.isEmpty())
	        throw new ParcelaQuitadaException("Recebimento não possui parcelas");

	    // Processa parcelas
	    processarParcelas(parcelasRecebimento, vlrecebido);

	    // Processa lançamento de caixa ou cartão
	    processarLancamento(recebimento, tituloEntity, vlrecebido);

	    // Atualiza recebimento
	    atualizarRecebimento(recebimento, vlrecebido, vlacrescimo, vldesconto);

	    return "Recebimento realizado com sucesso";
	}

	private void processarParcelas(List<Parcela> parcelas, Double vlrecebido) {
	    for (Parcela parcela : parcelas) {
	        if (vlrecebido <= 0)
	            break;

	        Double vlsobra = vlrecebido - parcela.getValor_restante();
	        vlsobra = Math.max(0, vlsobra);

	        Double vlquitado = Math.abs(vlsobra - vlrecebido);
	        vlrecebido = vlsobra;

	        try {
	            this.parcelas.receber(parcela.getCodigo(), vlquitado, 0.00, 0.00);
	        } catch (Exception e) {
	            throw new RecebimentoNotFoundException("Erro ao realizar o recebimento, chame o suporte");
	        }
	    }
	}

	private void processarLancamento(Recebimento recebimento, Titulo titulo, Double vllancamento) {
	    String sigla = titulo.getTipo().getSigla();

	    if (sigla.equals(TituloTipo.CARTDEB.toString()) || sigla.equals(TituloTipo.CARTCRED.toString())) {
	        cartaoLancamentos.lancamento(vllancamento, Optional.of(titulo));
	    } else {
	        Caixa caixaAberto = caixas.caixaAberto()
	            .orElseThrow(() -> new RuntimeException("Nenhum caixa aberto encontrado"));

	        Aplicacao aplicacao = new Aplicacao();
	        Usuario usuario = usuarios.buscaUsuario(aplicacao.getUsuarioAtual());

	        CaixaLancamento lancamento = new CaixaLancamento("Referente ao recebimento " + recebimento.getCodigo(),
	                vllancamento, TipoLancamento.RECEBIMENTO, EstiloLancamento.ENTRADA, caixaAberto, usuario);

	        lancamento.setRecebimento(recebimento);

	        try {
	            lancamentos.lancamento(lancamento);
	        } catch (Exception e) {
	            throw new LancamentoNotFoundException("Erro ao realizar o recebimento, chame o suporte");
	        }
	    }
	}

	private void atualizarRecebimento(Recebimento recebimento, Double vllancamento, Double vlacrescimo, Double vldesconto) {
	    try {
	        DataAtual dataAtual = new DataAtual();

	        recebimento.setValor_recebido(vllancamento);
	        recebimento.setValor_acrescimo(vlacrescimo);
	        recebimento.setValor_desconto(vldesconto);
	        recebimento.setData_processamento(dataAtual.dataAtualTimeStamp());

	        recebimentos.save(recebimento);
	    } catch (Exception e) {
	        throw new RecebimentoNotFoundException("Erro ao atualizar recebimento, chame o suporte");
	    }
	}

	private Double arredonda(Double valor) {
	    return Math.round(valor * 100.0) / 100.0;
	}


	public String remover(Long codigo) {
		Optional<Recebimento> recebimento = recebimentos.findById(codigo);

		if (recebimento.map(Recebimento::getData_processamento).isPresent())
			throw new RecebimentoNotFoundException("Esse recebimento não pode ser removido, pois ele já esta processado");

		try {
			recebimentos.deleteById(codigo);
		} catch (Exception e) {
			throw new RecebimentoNotFoundException("Erro ao remover orçamento, chame o suporte");
		}

		return "removido com sucesso";
	}

}
