package io.paymenthighway.connect;

import io.paymenthighway.PaymentHighwayUtility;
import io.paymenthighway.model.Token;
import io.paymenthighway.model.request.*;
import io.paymenthighway.model.request.Customer;
import io.paymenthighway.model.response.*;
import io.paymenthighway.model.response.transaction.DebitTransactionResponse;
import io.paymenthighway.security.SecureSigner;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.junit.*;

import java.io.IOException;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * PaymentAPIConnection test class
 */
public class PaymentAPIConnectionTest {

  private static String serviceUrl = null;
  private static String signatureKeyId = null;
  private static String signatureSecret = null;
  private static String account = null;
  private static String merchant = null;

  private static PaymentAPIConnection conn;

  private static final String RESULT_CODE_AUTHORIZATION_FAILED = "200";
  private static final String RESULT_CODE_UNMATCHED_REQUEST_PARAMETERS = "920";

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Properties p = PaymentHighwayUtility.getProperties();
    serviceUrl = p.getProperty("service_url");
    signatureKeyId = p.getProperty("signature_key_id");
    signatureSecret = p.getProperty("signature_secret");
    account = p.getProperty("sph-account");
    merchant = p.getProperty("sph-merchant");

    conn = new PaymentAPIConnection(serviceUrl, signatureKeyId, signatureSecret, account, merchant);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    if (conn != null) {
      conn.close();
    }
  }

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  private UUID createAndTestTransactionInit() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("Transaction init should succeed", "100", response.getResult().getCode());
    assertEquals("Transaction init should succeed", "OK", response.getResult().getMessage());

    assertNotNull("Transaction init should return id", response.getId());

    return response.getId();
  }

  private TokenizationResponse createAndTestTokenizationId(String cardNumber, String expirationMonth, String expirationYear, String cvc) {
    String formUri = FormAPIConnectionTest.createAndTestAddCardForm(serviceUrl, signatureKeyId, signatureSecret, account, merchant);

    String query = FormAPIConnectionTest.postCardFormAndReturnLastQueryString(serviceUrl, formUri, cardNumber, expirationMonth, expirationYear, cvc);

    Matcher matcher = Pattern.compile("(?<=sph-tokenization-id=).{36}").matcher(query);
    assertTrue(matcher.find());
    UUID tokenizationId = UUID.fromString(matcher.group());

    TokenizationResponse tokenResponse = null;
    try {
      tokenResponse = conn.tokenization(tokenizationId);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(tokenResponse);
    assertEquals(
        expirationYear,
        tokenResponse.getCard().getExpireYear()
    );
    assertEquals(
        cardNumber.substring(0, 6),
        tokenResponse.getCard().getBin()
    );
    assertEquals(
        "attempted",
        tokenResponse.getCardholderAuthentication()
    );
    assertNotNull(tokenResponse.getCustomer());

    return tokenResponse;
  }

  private Card testCardOkWithCvc = new Card("4153013999700024", "2017", "11", "024");
  private Card testCardTokenizeOkPaymentFails = new Card("4153013999700156", "2017", "11", "156");

  private void isSuccessfulCommitOrResultResponse(AbstractTransactionOutcomeResponse response) {
    assertNotNull(response);
    // Does not return card token when not paying with tokenized card
    //assertNotNull(commitTransactionResponse.getCardToken());
    assertTrue(response.getCard().getType().equalsIgnoreCase("visa"));
    assertEquals(response.getCard().getCvcRequired(), "not_tested");
    assertEquals(response.getCard().getBin(), testCardOkWithCvc.getPan().substring(0,6));
    assertEquals(response.getCard().getFunding(), "debit");
    assertEquals(response.getCard().getCategory(), "unknown");
    assertEquals(response.getCard().getCountryCode(), "FI");
    assertEquals(response.getCustomer().getNetworkAddress(), "83.145.208.186");
    assertEquals(response.getCustomer().getCountryCode(), "FI");
    assertEquals(response.getCardholderAuthentication(), "no");
    assertNotNull(response.getFilingCode());
    assertTrue(response.getFilingCode().length() == 12);
  }

  private UUID initAndDebitTransaction(Card card, Long amount, Boolean commit) {

    UUID transactionId = createAndTestTransactionInit();

    Customer customer = new Customer("83.145.208.186");

    TransactionRequest transaction = TransactionRequest.Builder(card, amount, "EUR")
        .setCustomer(customer)
        .setCommit(commit)
        .build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    return transactionId;
  }

  private void assertCommitted(AbstractTransactionOutcomeResponse response, Boolean isCommitted, String committedAmount) {
    assertNotNull(response.getCommitted());
    assertEquals(isCommitted, response.getCommitted());
    assertEquals(committedAmount, response.getCommittedAmount());
  }

  @Test
  public void testCreateSignature() {

    List<NameValuePair> sphHeaders = new ArrayList<>();

    sphHeaders.add(new BasicNameValuePair("sph-api-version", "20151028"));
    sphHeaders.add(new BasicNameValuePair("sph-account", "test"));
    sphHeaders.add(new BasicNameValuePair("sph-amount", "9990"));
    sphHeaders.add(new BasicNameValuePair("sph-timestamp", PaymentHighwayUtility.getUtcTimestamp()));
    sphHeaders.add(new BasicNameValuePair("sph-request-id", PaymentHighwayUtility.createRequestId()));

    String uri = "/transaction";
    SecureSigner ss = new SecureSigner(signatureKeyId, signatureSecret);
    String sig = ss.createSignature("POST", uri, sphHeaders, "");
    assertTrue(sig.contains("SPH1"));
  }

  @Test
  public void testInitTransaction() {
    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    // test for response code 100
    assertEquals(response.getResult().getCode(), "100");
    // test for message OK
    assertEquals(response.getResult().getMessage(), "OK");
    // test for id field
    assertTrue(response.getId() != null);
  }

  @Test
  public void testFailedAuthenticationInit() {
    try {
      conn.initTransactionHandle();
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("Authentication HMAC mismatch"));
    }
  }

  @Test
  public void testAddHeaders() {

    List<NameValuePair> sphHeaders = new ArrayList<>();

    sphHeaders.add(new BasicNameValuePair("sph-account", "test"));
    sphHeaders.add(new BasicNameValuePair("sph-amount", "9990"));
    sphHeaders.add(new BasicNameValuePair("sph-timestamp", PaymentHighwayUtility.getUtcTimestamp()));
    sphHeaders.add(new BasicNameValuePair("sph-request-id", PaymentHighwayUtility.createRequestId()));

    String uri = "/transaction";
    SecureSigner ss = new SecureSigner(signatureKeyId, signatureSecret);
    String sig = ss.createSignature("POST", uri, sphHeaders, "");
    sphHeaders.add(new BasicNameValuePair("signature", sig));

    HttpPost httpPost = new HttpPost(serviceUrl + uri);

    conn.addHeaders(httpPost, sphHeaders);

    assertTrue(httpPost.getAllHeaders().length == 7);

  }

  @Test
  public void testInitTransactionResponse() {

    InitTransactionResponse response = null;

    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());
  }

  /**
   * This will test successful debit transaction
   */
  @Test
  public void testDebitTransaction1() {

    UUID transactionId = createAndTestTransactionInit();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);

    TransactionRequest transaction = new TransactionRequest(card, "9999", "EUR");

    DebitTransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(
        "OK",
        transactionResponse.getResult().getMessage()
    );
    assertEquals(
        "100",
        transactionResponse.getResult().getCode()
    );
    assertNotNull(transactionResponse.getFilingCode());
    assertTrue(transactionResponse.getFilingCode().length() == 12);
  }

  private void performAndTestRejectedDebitRequest(UUID transactionId, Long amount) {

    TransactionRequest transaction = new TransactionRequest.Builder(testCardTokenizeOkPaymentFails, amount, "EUR").build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId,
          transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "Authorization failed");
    assertEquals(transactionResponse.getResult().getCode(), "200");
  }

  private CommitTransactionResponse commitTransaction(UUID transactionId, String amount, String currency) {
    CommitTransactionRequest commitTransaction = new CommitTransactionRequest(amount, currency);

    CommitTransactionResponse commitTransactionResponse = null;
    try {
      commitTransactionResponse = conn.commitTransaction(transactionId, commitTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return commitTransactionResponse;
  }

  private TransactionResultResponse transactionResult(UUID transactionId) {

    TransactionResultResponse transactionResultResponse = null;
    try {
      transactionResultResponse = conn.transactionResult(transactionId);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return transactionResultResponse;
  }

  /**
   * No limit available in the test bank account
   */
  @Test
  public void testDebitTransaction2() {

    UUID transactionId = createAndTestTransactionInit();

    performAndTestRejectedDebitRequest(transactionId, 9999L);
  }

  /**
   * Reported as stolen
   */
  @Test
  public void testDebitTransaction3() {

    UUID transactionId = createAndTestTransactionInit();

    String pan = "4153013999700289";
    String cvc = "289";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);

    TransactionRequest transaction = TransactionRequest.Builder(card, 99900, "EUR").build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "Authorization failed");
    assertEquals(transactionResponse.getResult().getCode(), "200");
  }

  /**
   * This will test successful debit transaction with the optional customer IP address
   */
  @Test
  public void testDebitTransaction4() {

    UUID transactionId = createAndTestTransactionInit();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);
    Customer customer = new Customer(InetAddress.getLoopbackAddress().getHostAddress());

    TransactionRequest transaction = TransactionRequest.Builder(card, 9999, "EUR").setCustomer(customer).build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId,
              transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // status response test
    TransactionStatusResponse statusResponse = null;

    try {
      statusResponse = conn.transactionStatus(transactionId);
    } catch (IOException e) {
      e.printStackTrace();
    }
    // test result
    assertNotNull(statusResponse);
    assertEquals(statusResponse.getResult().getMessage(), "OK");
    assertEquals(statusResponse.getResult().getCode(), "100");
    assertNotNull(statusResponse.getTransaction());
    assertNotNull(statusResponse.getTransaction().getStatus());
    assertEquals(
        "4000",
        statusResponse.getTransaction().getStatus().getCode()
    );
    assertEquals(
        "ok",
        statusResponse.getTransaction().getStatus().getState()
    );
    assertEquals(
        "9999",
        statusResponse.getTransaction().getCurrentAmount()
    );
    assertEquals(
        transactionId,
        statusResponse.getTransaction().getId()
    );
    assertEquals(
        "not_tested",
        statusResponse.getTransaction().getCard().getCvcRequired()
    );
  }

  /**
   * This will test successful tokenization payment without auto commit
   */
  @Test
  public void testDebitTransaction5() {

    TokenizationResponse tokenResponse = createAndTestTokenizationId("4153013999700024", "11", "2017", "024");

    assertEquals(tokenResponse.getCard().getCvcRequired(), "no");
    assertEquals(tokenResponse.getCard().getFunding(), "debit");
    assertEquals(tokenResponse.getCard().getCategory(), "unknown");

    UUID transactionId = createAndTestTransactionInit();

    TransactionRequest transaction = new TransactionRequest.Builder(new Token(tokenResponse.getCardToken()), 9999, "EUR")
        .setOrder("PIPPURI")
        .setCommit(false)
        .build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // status response test
    TransactionStatusResponse statusResponse = null;

    try {
      statusResponse = conn.transactionStatus(transactionId);
    } catch (IOException e) {
      e.printStackTrace();
    }
    // test result
    assertNotNull(statusResponse);
    assertEquals(statusResponse.getResult().getMessage(), "OK");
    assertEquals(statusResponse.getResult().getCode(), "100");
    assertNotNull(statusResponse.getTransaction());
    assertNotNull(statusResponse.getTransaction().getStatus());
    assertEquals(
        "4100",
        statusResponse.getTransaction().getStatus().getCode()
    );
    assertEquals(
        "ok_pending",
        statusResponse.getTransaction().getStatus().getState()
    );
    assertEquals(statusResponse.getTransaction().getCurrentAmount(), "9999");
    assertEquals(statusResponse.getTransaction().getId(), transactionId);
    assertEquals(statusResponse.getTransaction().getCard().getCvcRequired(), "no");
  }

  /**
   * This will test successful tokenization payment without auto commit and using token that requires cvc
   */
  @Test
  public void testDebitTransaction6() {

    TokenizationResponse tokenResponse = createAndTestTokenizationId("4324643990016048", "11", "2017", "048");

    assertEquals("yes", tokenResponse.getCard().getCvcRequired());
    assertEquals("unknown", tokenResponse.getCard().getFunding());
    assertEquals("unknown", tokenResponse.getCard().getCategory());

    UUID transactionId = createAndTestTransactionInit();

    TransactionRequest transaction = TransactionRequest.Builder(new Token(tokenResponse.getCardToken(), "048"), 9999, "EUR")
        .setOrder("PIPPURI")
        .setCommit(false)
        .build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // status response test
    TransactionStatusResponse statusResponse = null;

    try {
      statusResponse = conn.transactionStatus(transactionId);
    } catch (IOException e) {
      e.printStackTrace();
    }
    // test result
    assertNotNull(statusResponse);
    assertEquals(statusResponse.getResult().getMessage(), "OK");
    assertEquals(statusResponse.getResult().getCode(), "100");
    assertNotNull(statusResponse.getTransaction());
    assertNotNull(statusResponse.getTransaction().getStatus());
    assertEquals(
        "4100",
        statusResponse.getTransaction().getStatus().getCode()
    );
    assertEquals(
        "ok_pending",
        statusResponse.getTransaction().getStatus().getState()
    );
    assertEquals(statusResponse.getTransaction().getCurrentAmount(), "9999");
    assertEquals(statusResponse.getTransaction().getId(), transactionId);
    assertEquals(statusResponse.getTransaction().getCard().getCvcRequired(), "yes");
  }

  /**
   * This will test successful credit transaction NOTE: NOT YET IMPLEMENTED
   */
  @Ignore
  @Test
  public void testCreditTransaction1() {
    fail("Not yet implemented"); // TODO
  }

  /**
   * This will test successful revert transaction for full amount
   * <p/>
   * 1. create transaction handle 2. create a debit transaction 3. revert that
   * transaction
   */
  @Test
  public void testRevertTransaction1() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);

    TransactionRequest transaction = new TransactionRequest(card, "9999", "EUR");

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // revert transaction test
    TransactionResponse revertResponse = null;

    RevertTransactionRequest revertTransaction = new RevertTransactionRequest("9999", true);

    try {
      revertResponse = conn.revertTransaction(transactionId,
          revertTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(revertResponse);
    assertEquals(revertResponse.getResult().getMessage(), "OK");
    assertEquals(revertResponse.getResult().getCode(), "100");
  }

  /**
   * This will test successful revert transaction for partial amount
   * <p/>
   * 1. create transaction handle 2. create a debit transaction 3. revert that
   * transaction
   */
  @Test
  public void testRevertTransaction2() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);

    TransactionRequest transaction = new TransactionRequest(card, "9999", "EUR");

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId,
          transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // revert transaction test
    TransactionResponse revertResponse = null;

    RevertTransactionRequest revertTransaction = new RevertTransactionRequest("1000", true);

    try {
      revertResponse = conn.revertTransaction(transactionId, revertTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(revertResponse);
    assertEquals(revertResponse.getResult().getMessage(), "OK");
    assertEquals(revertResponse.getResult().getCode(), "100");

  }

  /**
   * This will test failed revert transaction because of insufficient balance
   * <p/>
   * 1. create transaction handle 2. create a debit transaction 3. revert that
   * transaction
   */
  @Test
  public void testRevertTransaction3() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);

    TransactionRequest transaction = new TransactionRequest(card, "1000", "EUR");

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // revert transaction test
    TransactionResponse revertResponse = null;

    RevertTransactionRequest revertTransaction = new RevertTransactionRequest("1001");

    try {
      revertResponse = conn.revertTransaction(transactionId, revertTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(revertResponse);
    assertTrue(revertResponse.getResult().getMessage().contains("Revert failed: Insufficient balance"));
    assertEquals(revertResponse.getResult().getCode(), "211");

  }

  /**
   * This will test successfull revert transaction for the full amount without
   * specifying amount
   * <p/>
   * 1. create transaction handle 2. create a debit transaction 3. revert that
   * transaction
   */
  @Test
  public void testRevertTransaction4() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);

    TransactionRequest transaction = new TransactionRequest(card, "1000", "EUR");

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId,
          transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // revert transaction test
    TransactionResponse revertResponse = null;

    RevertTransactionRequest revertTransaction = new RevertTransactionRequest();

    try {
      revertResponse = conn.revertTransaction(transactionId, revertTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(revertResponse);
    assertEquals(revertResponse.getResult().getMessage(), "OK");
    assertEquals(revertResponse.getResult().getCode(), "100");

  }

  /**
   * This will test successfull revert transaction for the full amount in two
   * different reverts
   * <p/>
   * 1. create transaction handle 2. create a debit transaction 3. revert half
   * transaction 4. revert rest of transaction
   */
  @Test
  public void testRevertTransaction5() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);

    TransactionRequest transaction = new TransactionRequest(card, "1000", "EUR");

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // revert transaction test
    TransactionResponse revertResponse = null;

    RevertTransactionRequest revertTransaction = new RevertTransactionRequest("500");

    try {
      revertResponse = conn.revertTransaction(transactionId, revertTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(revertResponse);
    assertEquals(revertResponse.getResult().getMessage(), "OK");
    assertEquals(revertResponse.getResult().getCode(), "100");

    revertTransaction = new RevertTransactionRequest(); // no amount set, should revert rest of the transaction
    try {
      revertResponse = conn.revertTransaction(transactionId, revertTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertEquals(revertResponse.getResult().getMessage(), "OK");
    assertEquals(revertResponse.getResult().getCode(), "100");
  }

  /**
   * This will test partial revert transactions where second revert will fail
   * due insufficient funds
   * <p/>
   * 1. create transaction handle 2. create a debit transaction 3. revert half
   * transaction 4. revert more than balance
   */
  @Test
  public void testRevertTransaction6() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);

    TransactionRequest transaction = new TransactionRequest(card, "1000", "EUR");

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // revert transaction test
    TransactionResponse revertResponse = null;

    RevertTransactionRequest revertTransaction = new RevertTransactionRequest("500");

    try {
      revertResponse = conn.revertTransaction(transactionId, revertTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(revertResponse);
    assertEquals(revertResponse.getResult().getMessage(), "OK");
    assertEquals(revertResponse.getResult().getCode(), "100");

    revertTransaction = new RevertTransactionRequest("501");// over balance
    try {
      revertResponse = conn.revertTransaction(transactionId, revertTransaction);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertTrue(revertResponse.getResult().getMessage().contains("Revert failed: Insufficient balance"));
    assertEquals(revertResponse.getResult().getCode(), "211");
  }

  /**
   * This will test successful transaction status request
   * <p/>
   * 1. create transaction handle 2. create a debit transaction 3. request a
   * transaction status
   */
  @Test
  public void testTransactionStatus1() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);
    Customer customer = new Customer("83.145.208.186");

    TransactionRequest transaction = TransactionRequest.Builder(card, 9999, "EUR").setCustomer(customer).build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // status response test
    TransactionStatusResponse statusResponse = null;

    try {
      statusResponse = conn.transactionStatus(transactionId);
    } catch (IOException e) {
      e.printStackTrace();
    }
    // test result
    assertNotNull(statusResponse);
    assertEquals(statusResponse.getResult().getMessage(), "OK");
    assertEquals(statusResponse.getResult().getCode(), "100");
    assertEquals(statusResponse.getTransaction().getCurrentAmount(), "9999");
    assertEquals(statusResponse.getTransaction().getId(), transactionId);
    assertEquals(statusResponse.getTransaction().getCard().getCvcRequired(), "not_tested");
    assertEquals(statusResponse.getTransaction().getCard().getBin(), "415301");
    assertEquals(statusResponse.getTransaction().getCard().getFunding(), "debit");
    assertEquals(statusResponse.getTransaction().getCard().getCategory(), "unknown");
    assertEquals(statusResponse.getTransaction().getCard().getCountryCode(), "FI");
    assertEquals(statusResponse.getTransaction().getCustomer().getNetworkAddress(), "83.145.208.186");
    assertEquals(statusResponse.getTransaction().getCustomer().getCountryCode(), "FI");
    assertEquals(statusResponse.getTransaction().getCardholderAuthentication(), "no");
    assertEquals(true, statusResponse.getTransaction().getCommitted());
    assertEquals("9999", statusResponse.getTransaction().getCommittedAmount());
  }

  /**
   * This will test successful transaction status request with the option "order" parameter and no auto-commit
   * <p/>
   * 1. create transaction handle 2. create a debit transaction 3. request a
   * transaction status
   */
  @Test
  public void testTransactionStatus2() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);
    Customer customer = new Customer("83.145.208.186");
    String orderId = "ABC123";

    TransactionRequest transaction = TransactionRequest.Builder(card, 9999, "EUR").setOrder(orderId).setCommit(false).setCustomer(customer).build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // status response test
    TransactionStatusResponse statusResponse = null;

    try {
      statusResponse = conn.transactionStatus(transactionId);
    } catch (IOException e) {
      e.printStackTrace();
    }
    // test result
    assertNotNull(statusResponse);
    assertEquals(statusResponse.getResult().getMessage(), "OK");
    assertEquals(statusResponse.getResult().getCode(), "100");
    assertEquals(statusResponse.getTransaction().getCurrentAmount(), "9999");
    assertEquals(statusResponse.getTransaction().getId(), transactionId);
    assertEquals(statusResponse.getTransaction().getCard().getCvcRequired(), "not_tested");
    assertEquals(statusResponse.getTransaction().getCard().getBin(), "415301");
    assertEquals(statusResponse.getTransaction().getCard().getFunding(), "debit");
    assertEquals(statusResponse.getTransaction().getCard().getCategory(), "unknown");
    assertEquals(statusResponse.getTransaction().getCard().getCountryCode(), "FI");
    assertEquals(statusResponse.getTransaction().getCustomer().getNetworkAddress(), "83.145.208.186");
    assertEquals(statusResponse.getTransaction().getCustomer().getCountryCode(), "FI");
    assertEquals(statusResponse.getTransaction().getCardholderAuthentication(), "no");
    assertEquals(statusResponse.getTransaction().getOrder(), orderId);
    assertEquals(statusResponse.getTransaction().getCommitted(), false);
    assertEquals(statusResponse.getTransaction().getCommittedAmount(), null);

  }

  /**
   * This will test a successful order search request
   * <p/>
   * 1. create transaction handle 2. create a debit transaction with order id 3. search by the order id
   */
  @Test
  public void testOrderSearch1() {

    InitTransactionResponse response = null;
    try {
      response = conn.initTransactionHandle();
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(response);
    assertEquals("100", response.getResult().getCode());
    assertEquals("OK", response.getResult().getMessage());

    UUID transactionId = response.getId();

    String pan = "4153013999700024";
    String cvc = "024";
    String expiryYear = "2017";
    String expiryMonth = "11";
    Card card = new Card(pan, expiryYear, expiryMonth, cvc);
    UUID orderId = UUID.randomUUID();
    Customer customer = new Customer("83.145.208.186");

    TransactionRequest transaction = TransactionRequest.Builder(card, 9999, "EUR").setOrder(orderId.toString()).setCustomer(customer).build();

    TransactionResponse transactionResponse = null;
    try {
      transactionResponse = conn.debitTransaction(transactionId, transaction);
    } catch (IOException e) {
      e.printStackTrace();
    }

    assertNotNull(transactionResponse);
    assertEquals(transactionResponse.getResult().getMessage(), "OK");
    assertEquals(transactionResponse.getResult().getCode(), "100");

    // order search test
    OrderSearchResponse orderSearchResponse = null;

    try {
      orderSearchResponse = conn.searchOrders(orderId.toString());
    } catch (IOException e) {
      e.printStackTrace();
    }

    // test result
    assertNotNull(orderSearchResponse);
    assertEquals(orderSearchResponse.getResult().getMessage(), "OK");
    assertEquals(orderSearchResponse.getResult().getCode(), "100");
    assertEquals(orderSearchResponse.getTransactions()[0].getCurrentAmount(), "9999");
    assertEquals(orderSearchResponse.getTransactions()[0].getId(), transactionId);
    assertEquals(orderSearchResponse.getTransactions()[0].getCard().getBin(), "415301");
    assertEquals(orderSearchResponse.getTransactions()[0].getCard().getFunding(), "debit");
    assertEquals(orderSearchResponse.getTransactions()[0].getCard().getCategory(), "unknown");
    assertEquals(orderSearchResponse.getTransactions()[0].getCard().getCountryCode(), "FI");
    assertEquals(orderSearchResponse.getTransactions()[0].getCustomer().getNetworkAddress(), "83.145.208.186");
    assertEquals(orderSearchResponse.getTransactions()[0].getCustomer().getCountryCode(), "FI");
    assertEquals(orderSearchResponse.getTransactions()[0].getCardholderAuthentication(), "no");
    assertEquals(true, orderSearchResponse.getTransactions()[0].getCommitted());
    assertEquals("9999", orderSearchResponse.getTransactions()[0].getCommittedAmount());
  }

  /**
   * This will test successful com transaction
   */
  @Test
  public void testCommitTransaction1() {

    UUID transactionId = initAndDebitTransaction(testCardOkWithCvc, 9999L, false);

    CommitTransactionResponse commitTransactionResponse = commitTransaction(transactionId, "9999", "EUR");
    isSuccessfulCommitOrResultResponse(commitTransactionResponse);
    assertCommitted(commitTransactionResponse, true, "9999");
  }

  @Test
  public void testCommitTransactionWithLessThanTotalAmount() {

    UUID transactionId = initAndDebitTransaction(testCardOkWithCvc, 9999L, false);

    CommitTransactionResponse commitTransactionResponse = commitTransaction(transactionId, "5000", "EUR");
    isSuccessfulCommitOrResultResponse(commitTransactionResponse);
    assertCommitted(commitTransactionResponse, true, "5000");
  }

  @Test
  public void testCommitTransactionRejectedWithAmountGreaterThanPayment() {

    UUID transactionId = initAndDebitTransaction(testCardOkWithCvc, 9999L, false);

    CommitTransactionResponse commitTransactionResponse = commitTransaction(transactionId, "10000", "EUR");
    assertEquals(RESULT_CODE_UNMATCHED_REQUEST_PARAMETERS, commitTransactionResponse.getResult().getCode());
  }

  @Test
  public void testCommitTransactionTwiceWithSameValuesSucceeds() {

    UUID transactionId = initAndDebitTransaction(testCardOkWithCvc, 9999L, false);

    CommitTransactionResponse commitTransactionResponse = commitTransaction(transactionId, "9999", "EUR");
    isSuccessfulCommitOrResultResponse(commitTransactionResponse);
    assertCommitted(commitTransactionResponse, true, "9999");

    CommitTransactionResponse commitTransactionResponse2 = commitTransaction(transactionId, "9999", "EUR");
    isSuccessfulCommitOrResultResponse(commitTransactionResponse2);
    assertCommitted(commitTransactionResponse2, true, "9999");
  }

  @Test
  public void testCommitTransactionTwiceWithDifferentValueDoesNotChangeTheInitialCommittedValue() {

    UUID transactionId = initAndDebitTransaction(testCardOkWithCvc, 9999L, false);

    CommitTransactionResponse commitTransactionResponse = commitTransaction(transactionId, "9999", "EUR");
    isSuccessfulCommitOrResultResponse(commitTransactionResponse);
    assertCommitted(commitTransactionResponse, true, "9999");

    CommitTransactionResponse commitTransactionResponse2 = commitTransaction(transactionId, "5000", "EUR");
    isSuccessfulCommitOrResultResponse(commitTransactionResponse2);
    assertCommitted(commitTransactionResponse2, true, "9999");
  }

  @Test
  public void testTransactionResultReturnsCommittedAndUncommittedValues() {

    UUID transactionId = initAndDebitTransaction(testCardOkWithCvc, 9999L, false);

    TransactionResultResponse transactionResultResponse1 = transactionResult(transactionId);
    isSuccessfulCommitOrResultResponse(transactionResultResponse1);
    assertCommitted(transactionResultResponse1, false, null);

    CommitTransactionResponse commitTransactionResponse = commitTransaction(transactionId, "9999", "EUR");
    isSuccessfulCommitOrResultResponse(commitTransactionResponse);
    assertCommitted(commitTransactionResponse, true, "9999");

    TransactionResultResponse transactionResultResponse2 = transactionResult(transactionId);
    isSuccessfulCommitOrResultResponse(transactionResultResponse2);
    assertCommitted(transactionResultResponse2, true, "9999");
  }

  @Test
  public void testTransactionResultReturnsFailureIfRejectedPayment() {
    UUID transactionId = createAndTestTransactionInit();

    performAndTestRejectedDebitRequest(transactionId, 9999L);

    TransactionResultResponse transactionResultResponse = transactionResult(transactionId);

    assertEquals(RESULT_CODE_AUTHORIZATION_FAILED, transactionResultResponse.getResult().getCode());
  }

  /**
   * This will test successful tokenization request
   */
  @Test
  public void testTokenization1() {

    // TODO: fetch this from Form API get parameters
    UUID tokenizationId = UUID.fromString("08cc223a-cf93-437c-97a2-f338eaf0d860");

    TokenizationResponse tokenResponse = null;
    try {
      tokenResponse = conn.tokenization(tokenizationId);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(tokenResponse);
    assertEquals(tokenResponse.getCard().getExpireYear(), "2017");
    assertEquals(tokenResponse.getCardToken().toString(), "71435029-fbb6-4506-aa86-8529efb640b0");
    assertEquals(tokenResponse.getCard().getCvcRequired(), "no");
    assertEquals(tokenResponse.getCard().getBin(), "415301");
    assertEquals(tokenResponse.getCard().getFunding(), "debit");
    assertEquals(tokenResponse.getCard().getCategory(), "unknown");
    assertEquals(tokenResponse.getCard().getCountryCode(), "FI");
    // no Customer info was available when the tokenizationId was generated so it should not be visible in the response
    assertNull(tokenResponse.getCustomer());
    assertEquals(tokenResponse.getCardholderAuthentication(), "no");
  }

  /**
   * This will test successful tokenization request, with Customer info in the response
   */
  @Test
  public void testTokenization2() {

    // TODO: fetch this from Form API get parameters
    UUID tokenizationId = UUID.fromString("475d49ec-2c37-4ae5-a6ef-33dc6b60ac71");

    TokenizationResponse tokenResponse = null;
    try {
      tokenResponse = conn.tokenization(tokenizationId);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(tokenResponse);
    assertEquals(tokenResponse.getCard().getExpireYear(), "2017");
    assertEquals(tokenResponse.getCardToken().toString(), "71435029-fbb6-4506-aa86-8529efb640b0");
    assertEquals(tokenResponse.getCard().getCvcRequired(), "no");
    assertEquals(tokenResponse.getCard().getBin(), "415301");
    assertEquals(tokenResponse.getCard().getFunding(), "debit");
    assertEquals(tokenResponse.getCard().getCategory(), "unknown");
    assertEquals(tokenResponse.getCard().getCountryCode(), "FI");
    // Customer information from the time the tokenizationId was generated through the Form API add_card request
    assertEquals(tokenResponse.getCustomer().getNetworkAddress(), "83.145.208.185"); // Manually updated :/
    assertEquals(tokenResponse.getCustomer().getCountryCode(), "FI");
    assertEquals(tokenResponse.getCardholderAuthentication(), "no");
  }

  /**
   * This will test successful batch report request
   */
  @Test
  public void testReport1() {

    // request batch for yesterday, today is not available
    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DATE, -1);
    String date = dateFormat.format(cal.getTime());

    ReportResponse result = null;
    try {
      result = conn.fetchReport(date);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(result);
    assertEquals(result.getResult().getCode(), "100");
    assertEquals(result.getResult().getMessage(), "OK");
    assertEquals(result.getSettlements()[0].getMerchant().getAcquirerMerchantId(), "90000001");
  }

  /**
   * This will test successful reconciliation report request
   */
  @Ignore
  @Test
  public void testReconciliationReport() {

    fail("No test report available yet");

    // request settlement report for yesterday, today is not available
    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DATE, -1);
    String date = dateFormat.format(cal.getTime());

    ReconciliationReportResponse result = null;
    try {
      result = conn.fetchReconciliationReport(date);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(result);
    assertEquals(result.getResult().getCode(), "100");
    assertEquals(result.getResult().getMessage(), "OK");
    assertNotNull(result.getReconciliationSettlements()[0].getAcquirer());
    assertNotNull(result.getReconciliationSettlements()[0].getAcquirerBatchId());
    assertNotNull(result.getReconciliationSettlements()[0].getBatch());
    assertNotNull(result.getReconciliationSettlements()[0].getCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getDateProcessed());
    assertNotNull(result.getReconciliationSettlements()[0].getMainAcquirerMerchantId());
    assertNotNull(result.getReconciliationSettlements()[0].getNetAmount());
    assertNotNull(result.getReconciliationSettlements()[0].getReference());
    assertNotNull(result.getReconciliationSettlements()[0].getStatus());
    assertNotEquals(result.getReconciliationSettlements()[0].getTransactionCount(), "0");
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getMerchant());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerAmountPresented());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerCommission());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerCommissionCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerDiscountRate());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerEstimatedSettlementValue());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerEstimatedSettlementValueCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerExchangeRate());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerTransactionFee());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerTransactionFeeCurrency());
    assertEquals(result.getReconciliationSettlements()[0].getUnallocatedTransactionsCount(), "0");

  }

  /**
   * This will test successful reconciliation report request with unallocated transactions
   */
  @Ignore
  @Test
  public void testReconciliationReport2() {

    fail("No test report available yet");

    // request settlement report for yesterday, today is not available
    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DATE, -1);
    String date = dateFormat.format(cal.getTime());

    ReconciliationReportResponse result = null;
    try {
      result = conn.fetchReconciliationReport(date);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(result);
    assertEquals(result.getResult().getCode(), "100");
    assertEquals(result.getResult().getMessage(), "OK");
    assertNotNull(result.getReconciliationSettlements()[0].getAcquirer());
    assertNotNull(result.getReconciliationSettlements()[0].getAcquirerBatchId());
    assertNotNull(result.getReconciliationSettlements()[0].getBatch());
    assertNotNull(result.getReconciliationSettlements()[0].getCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getDateProcessed());
    assertNotNull(result.getReconciliationSettlements()[0].getMainAcquirerMerchantId());
    assertNotNull(result.getReconciliationSettlements()[0].getNetAmount());
    assertNotNull(result.getReconciliationSettlements()[0].getReference());
    assertNotNull(result.getReconciliationSettlements()[0].getStatus());
    assertEquals(result.getReconciliationSettlements()[0].getTransactionCount(), "0");
    assertNotEquals(Integer.parseInt(result.getReconciliationSettlements()[0].getUnallocatedTransactionsCount()), 0);
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getFilingCode());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerAmountPresented());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerCommission());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerCommissionCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerDiscountRate());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerEstimatedSettlementValue());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerEstimatedSettlementValueCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerExchangeRate());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerTransactionFee());
    assertNotNull(result.getReconciliationSettlements()[0].getUnallocatedTransactions()[0].getAcquirerTransactionFeeCurrency());
  }

  /**
   * This will test successful reconciliation report request with commission settlement data included
   */
  @Ignore
  @Test
  public void  testReconciliationReport3() {

    fail("No test report available yet");

    // request settlement report for yesterday, today is not available
    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DATE, -1);
    String date = dateFormat.format(cal.getTime());

    ReconciliationReportResponse result = null;
    try {
      result = conn.fetchReconciliationReport(date);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertNotNull(result);
    assertEquals(result.getResult().getCode(), "100");
    assertEquals(result.getResult().getMessage(), "OK");
    assertNotNull(result.getReconciliationSettlements()[0].getAcquirer());
    assertNotNull(result.getReconciliationSettlements()[0].getAcquirerBatchId());
    assertNotNull(result.getReconciliationSettlements()[0].getBatch());
    assertNotNull(result.getReconciliationSettlements()[0].getCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getDateProcessed());
    assertNotNull(result.getReconciliationSettlements()[0].getMainAcquirerMerchantId());
    assertNotNull(result.getReconciliationSettlements()[0].getNetAmount());
    assertNotNull(result.getReconciliationSettlements()[0].getReference());
    assertNotNull(result.getReconciliationSettlements()[0].getStatus());
    assertNotEquals(result.getReconciliationSettlements()[0].getTransactionCount(), "0");
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getMerchant());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerAmountPresented());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerCommission());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerCommissionCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerDiscountRate());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerEstimatedSettlementValue());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerEstimatedSettlementValueCurrency());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerExchangeRate());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerTransactionFee());
    assertNotNull(result.getReconciliationSettlements()[0].getTransactions()[0].getAcquirerTransactionFeeCurrency());
    assertNotNull(result.getCommissionSettlements());
    assertNotNull(result.getCommissionSettlements()[0].getAcquirer());
    assertNotNull(result.getCommissionSettlements()[0].getAcquirerBatchId());
    assertNotNull(result.getCommissionSettlements()[0].getAmount());
    assertNotNull(result.getCommissionSettlements()[0].getBatch());
    assertNotNull(result.getCommissionSettlements()[0].getCurrency());
    assertNotNull(result.getCommissionSettlements()[0].getDateProcessed());
    assertNotNull(result.getCommissionSettlements()[0].getMainAcquirerMerchantId());
    assertNotNull(result.getCommissionSettlements()[0].getReference());

  }

}