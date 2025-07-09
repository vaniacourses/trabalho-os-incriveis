package net.originmobi.pdv.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VendaSeleniumTest {


    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\rober\\Downloads\\chromedriver-win64\\chromedriver.exe");
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
    @Order(1)
    void logouComSucesso() {
        WebElement sairButton = driver.findElement(By.className("btn-sair"));
        Assertions.assertNotNull(sairButton);
    }

    @Test
    @Order(2)
    void abrirCaixa() {
        WebElement caixaLink = wait.until(ExpectedConditions.presenceOfElementLocated((By.xpath("//a[@href='/caixa']"))));
        caixaLink.click();
        // Find the "Abrir Novo" button by its link text or class
        WebElement abrirNovoBtn = wait.until(ExpectedConditions.presenceOfElementLocated(By.linkText("Abrir Novo")));

        // Assert it is displayed
        assertTrue(abrirNovoBtn.isDisplayed());

        // Click the button
        abrirNovoBtn.click();
        WebElement valorInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("valorAbertura")));
        valorInput.clear();
        valorInput.sendKeys("10000");

        WebElement form = driver.findElement(By.id("form_caixa"));
        form.submit();

        // Step 4: Wait for redirect
        wait.until(ExpectedConditions.urlContains("/caixa"));

        assertTrue(driver.getCurrentUrl().contains("/caixa"));
    }

    @Test
    @Order(3)
    void acessarPaginaPedidos() {
        WebElement pedidosLink = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//a[@href='/venda/status/ABERTA']")));
        pedidosLink.click();
        WebElement tituloPedidos = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//h1[@class='titulo-h1' and text()='Pedidos']")));
        Assertions.assertNotNull(tituloPedidos);
    }

    @Test
    @Order(4)
    void abrirVenda() {
        WebElement pedidosLink = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//a[@href='/venda/status/ABERTA']")));
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
    @Order(5)
    void fecharVendaAVista() {
        WebElement pedidosLink = wait.until(ExpectedConditions.presenceOfElementLocated((By.xpath("//a[@href='/venda/status/ABERTA']"))));
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

}
