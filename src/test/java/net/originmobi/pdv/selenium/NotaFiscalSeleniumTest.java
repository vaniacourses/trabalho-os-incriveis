package net.originmobi.pdv.selenium;

import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.Alert;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.util.List;

public class NotaFiscalSeleniumTest {

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        System.setProperty("webdriver.chrome.driver", "C:/Users/Pichau/Downloads/chromedriver-win64/chromedriver.exe");
        driver = new ChromeDriver();
        wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/login");

        // Login
        driver.findElement(By.id("user")).sendKeys("gerente");
        driver.findElement(By.id("password")).sendKeys("123");
        driver.findElement(By.id("btn-login")).click();
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    private void criaNotaEEntraNaPaginaDeDetalhes() {
        // Navega para a lista de notas fiscais
        WebElement menuNotaFiscal = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[@href='/notafiscal']")));
        menuNotaFiscal.click();
        
        // Clica no botão para abrir o formulário de nova nota
        WebElement bttnNotaFiscal = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[@href='/notafiscal/form']")));
        bttnNotaFiscal.click();

        // Preenche o formulário
        WebElement dropdownDestinatario = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[data-id='destinatario']")));
        dropdownDestinatario.click();

        WebElement primeiraOpcao = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div.dropdown-menu.open ul li[data-original-index='1'] a")));
        primeiraOpcao.click();

        WebElement naturezaInput = driver.findElement(By.id("natureza"));
        naturezaInput.sendKeys("Venda de Teste Selenium");

        // Clica para criar a nota
        WebElement criarNotaButton = driver.findElement(By.className("btn-cria-nota"));
        criarNotaButton.click();

        // Espera o redirecionamento para a página de detalhes
        wait.until(ExpectedConditions.urlMatches(".*/notafiscal/\\d+$"));
    }
    
    @Test
    void logouComSucesso() {
        WebElement sairButton = driver.findElement(By.className("btn-sair"));
        assertNotNull(sairButton);
    }
    
    @Test
    void deveCriarNotaERedirecionarCorretamente() {
        // Executa o fluxo de criação da nota
        criaNotaEEntraNaPaginaDeDetalhes();
        
        // Apenas verifica se a URL final está correta
        String urlFinal = driver.getCurrentUrl();
        assertTrue(urlFinal.matches(".*/notafiscal/\\d+$"), "A URL final (" + urlFinal + ") não corresponde ao padrão esperado '/notafiscal/{id}'.");
        
        System.out.println("Teste de criação e redirecionamento concluído! URL final: " + urlFinal);
    }

    @Test
    void deveEmitirNfeEReceberAlertaDeConfirmacao() {
        // ETAPA 1: Usa o método de ajuda para criar uma nota e ir para sua página
        criaNotaEEntraNaPaginaDeDetalhes();
        System.out.println("Nota criada. Testando a emissão na página: " + driver.getCurrentUrl());
        
        // ETAPA 2: Executa a ação de emitir e verifica o alerta
        WebElement emitirButton = wait.until(ExpectedConditions.elementToBeClickable(By.className("btn-emitir-nfe")));
        emitirButton.click();

        // Espera o alerta aparecer
        Alert alert = wait.until(ExpectedConditions.alertIsPresent());

        // Verifica o texto do alerta
        String textoDoAlerta = alert.getText();
        assertEquals("ok", textoDoAlerta, "O texto do alerta não era o esperado.");
        System.out.println("Alerta recebido com o texto correto: '" + textoDoAlerta + "'");

        // Aceita o alerta para fechar
        alert.accept();
    }
}
