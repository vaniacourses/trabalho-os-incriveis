package net.originmobi.pdv.service;

import net.originmobi.pdv.model.Cidade;
import net.originmobi.pdv.model.Endereco;
import net.originmobi.pdv.model.Fornecedor;
import net.originmobi.pdv.model.Telefone;
import net.originmobi.pdv.enumerado.TelefoneTipo;
import net.originmobi.pdv.repository.FornecedorRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class FornecedorServiceTest {

    private FornecedorRepository fornecedorRepository;
    private FornecedorService fornecedorService;

    @BeforeEach
    public void setup() throws Exception {
        fornecedorRepository = Mockito.mock(FornecedorRepository.class);
        fornecedorService = new FornecedorService();

        // Usar reflexão para injetar o mock no campo private
        Field field = FornecedorService.class.getDeclaredField("fornecedores");
        field.setAccessible(true);
        field.set(fornecedorService, fornecedorRepository);
    }

    @Test
    public void testCadastrarNovoFornecedorComSucesso() {
        Fornecedor novoFornecedor = criarFornecedor(null);

        when(fornecedorRepository.findByCnpj(any())).thenReturn(null);
        when(fornecedorRepository.save(any(Fornecedor.class))).thenReturn(novoFornecedor);

        String resultado = fornecedorService.cadastrar(novoFornecedor);

        assertEquals("Fornecedor salvo com sucesso", resultado);
        verify(fornecedorRepository, times(1)).save(any(Fornecedor.class));
    }


    @Test
    public void testAtualizarFornecedorExistente() {
        Fornecedor fornecedor = criarFornecedor(1L);

        when(fornecedorRepository.findByCodigo(1L)).thenReturn(fornecedor);
        when(fornecedorRepository.save(any(Fornecedor.class))).thenReturn(fornecedor);

        String resultado = fornecedorService.cadastrar(fornecedor);

        assertEquals("Fornecedor salvo com sucesso", resultado);
        verify(fornecedorRepository, times(1)).save(any(Fornecedor.class));
    }

    // Método auxiliar para criar fornecedor
    private Fornecedor criarFornecedor(Long id) {
        Cidade cidade = new Cidade();
        cidade.setNome("São Paulo");

        Endereco endereco = new Endereco();
        endereco.setRua("Rua Exemplo");
        endereco.setBairro("Centro");
        endereco.setNumero("123");
        endereco.setCep("12345-678");
        endereco.setReferencia("Próximo ao metrô");
        endereco.setCidade(cidade);

        Telefone telefone = new Telefone();
        telefone.setFone("11999999999");
        telefone.setTipo(TelefoneTipo.CELULAR);

        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setCodigo(id);
        fornecedor.setCnpj("12.345.678/0001-90");
        fornecedor.setNome("Fornecedor Teste");
        fornecedor.setNome_fantasia("Fantasia Teste");
        fornecedor.setInscricao_estadual("ISENTO");
        fornecedor.setAtivo(1);
        fornecedor.setObservacao("Observação de teste");
        fornecedor.setEndereco(endereco);
        fornecedor.setTelefone(Arrays.asList(telefone));

        return fornecedor;
    }
}




