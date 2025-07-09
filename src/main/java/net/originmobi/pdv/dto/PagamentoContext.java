package net.originmobi.pdv.dto;

import net.originmobi.pdv.model.Receber;
import net.originmobi.pdv.model.Venda;
import net.originmobi.pdv.utilitarios.DataAtual;

public class PagamentoContext {
    public String[] formaPagar;
    public String[] titulos;
    public Double vlprodutos;
    public String[] vlParcelas;
    public Double desc;
    public Double acre;
    public Venda dadosVenda;
    public DataAtual dataAtual;
    public Receber receber;

    public PagamentoContext(String[] formaPagar, String[] titulos, Double vlprodutos, String[] vlParcelas,
                            Double desc, Double acre, Venda dadosVenda, DataAtual dataAtual, Receber receber) {
        this.formaPagar = formaPagar;
        this.titulos = titulos;
        this.vlprodutos = vlprodutos;
        this.vlParcelas = vlParcelas;
        this.desc = desc;
        this.acre = acre;
        this.dadosVenda = dadosVenda;
        this.dataAtual = dataAtual;
        this.receber = receber;
    }
}
