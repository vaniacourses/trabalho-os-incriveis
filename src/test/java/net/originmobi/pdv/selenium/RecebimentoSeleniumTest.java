package net.originmobi.pdv.selenium;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.List;

public class RecebimentoSeleniumTest {

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver");
        driver = new ChromeDriver();
        wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/login");

        WebElement usernameField = driver.findElement(By.id("user"));
        usernameField.clear();
        usernameField.sendKeys("gerente");

        WebElement passwordField = driver.findElement(By.id("password"));
        passwordField.clear();
        passwordField.sendKeys("123");

        WebElement loginButton = driver.findElement(By.id("btn-login"));
        loginButton.click();
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void logouComSucesso() {
        WebElement sairButton = driver.findElement(By.className("btn-sair"));
        assertNotNull(sairButton);
    }

    @Test
    void acessarPaginaPedidos() {
        WebElement pedidosLink = driver.findElement(By.xpath("//a[@href='/venda/status/ABERTA']"));
        pedidosLink.click();
        WebElement tituloPedidos = driver.findElement(By.xpath("//h1[@class='titulo-h1' and text()='Pedidos']"));
        assertNotNull(tituloPedidos);
    }

    @Test
    void acessarPedidoEmAberto() {
        WebElement pedidosLink = driver.findElement(By.xpath("//a[@href='/venda/status/ABERTA']"));
        pedidosLink.click();
        WebElement tituloPedidos = driver.findElement(By.xpath("//h1[@class='titulo-h1' and text()='Pedidos']"));
        assertNotNull(tituloPedidos);
        WebElement pedidoLink = driver.findElement(By.className("panel-body"));
        pedidoLink.click();
        WebElement statusAberta = driver.findElement(By.xpath("//span[@class='label-sucess' and text()='ABERTA']"));
        assertNotNull(statusAberta);
    }

    @Test
    void abrirFormDeVenda() {
        WebElement pedidosLink = driver.findElement(By.xpath("//a[@href='/venda/status/ABERTA']"));
        pedidosLink.click();
        WebElement tituloPedidos = driver.findElement(By.xpath("//h1[@class='titulo-h1' and text()='Pedidos']"));
        assertNotNull(tituloPedidos);
        WebElement pedidoLink = driver.findElement(By.className("panel-body"));
        pedidoLink.click();
        WebElement statusAberta = driver.findElement(By.xpath("//span[@class='label-sucess' and text()='ABERTA']"));
        assertNotNull(statusAberta);
        WebElement gerarVendaButton = driver.findElement(By.id("btn-venda"));
        gerarVendaButton.click();
        WebElement pagamentoTitulo = driver.findElement(By.xpath("//h2[text()='Pagamento']"));
        assertNotNull(pagamentoTitulo);
    }

    @Test
    void fecharVendaAVista() {
        WebElement pedidosLink = driver.findElement(By.xpath("//a[@href='/venda/status/ABERTA']"));
        pedidosLink.click();
        WebElement tituloPedidos = driver.findElement(By.xpath("//h1[@class='titulo-h1' and text()='Pedidos']"));
        assertNotNull(tituloPedidos);

        WebElement pedidoLink = driver.findElement(By.className("panel-body"));
        pedidoLink.click();
        WebElement statusAberta = driver.findElement(By.xpath("//span[@class='label-sucess' and text()='ABERTA']"));
        assertNotNull(statusAberta);

        WebElement gerarVendaButton = driver.findElement(By.id("btn-venda"));
        gerarVendaButton.click();

        WebElement pagamentoTitulo = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h2[text()='Pagamento']")));
        assertNotNull(pagamentoTitulo);

        WebElement pagamentoSelectElement = wait.until(ExpectedConditions.elementToBeClickable(By.id("pagamento")));
        Select pagamentoSelect = new Select(pagamentoSelectElement);
        pagamentoSelect.selectByVisibleText("À Vista");

        WebElement pagarButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[@href='/venda/fechar/' and contains(@class, 'btn-pagamento')]")));
        pagarButton.click();

        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        alert.accept();

        WebElement statusFechada = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[@class='label-danger' and text()='FECHADA']")));
        assertNotNull(statusFechada);
    }
    
    @Test
    void fecharVendaAVistaEVerificarRecebimentos() {
        WebElement pedidosLink = driver.findElement(By.xpath("//a[@href='/venda/status/ABERTA']"));
        pedidosLink.click();
        WebElement tituloPedidos = driver.findElement(By.xpath("//h1[@class='titulo-h1' and text()='Pedidos']"));
        assertNotNull(tituloPedidos);

        WebElement pedidoLink = driver.findElement(By.className("panel-body"));
        pedidoLink.click();
        WebElement statusAberta = driver.findElement(By.xpath("//span[@class='label-sucess' and text()='ABERTA']"));
        assertNotNull(statusAberta);

        WebElement gerarVendaButton = driver.findElement(By.id("btn-venda"));
        gerarVendaButton.click();

        WebElement pagamentoTitulo = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h2[text()='Pagamento']")));
        assertNotNull(pagamentoTitulo);

        WebElement pagamentoSelectElement = wait.until(ExpectedConditions.elementToBeClickable(By.id("pagamento")));
        Select pagamentoSelect = new Select(pagamentoSelectElement);
        pagamentoSelect.selectByVisibleText("À Vista");

        WebElement pagarButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[@href='/venda/fechar/' and contains(@class, 'btn-pagamento')]")));
        pagarButton.click();

        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        alert.accept();

        WebElement statusFechada = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[@class='label-danger' and text()='FECHADA']")));
        assertNotNull(statusFechada);

        WebElement receberLink = driver.findElement(By.xpath("//a[@href='/receber']"));
        receberLink.click();

        WebElement tabelaReceber = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#table-parcelas-cliente table.tabela-receber")));
        List<WebElement> linhasTabela = tabelaReceber.findElements(By.tagName("tr"));
        assertNotNull(linhasTabela);
        assertEquals(false, !linhasTabela.isEmpty());
    }
}
