package io.paymenthighway.connect;

import io.paymenthighway.PaymentHighwayUtility;
import io.paymenthighway.exception.ErrorResponseException;
import io.paymenthighway.json.JsonGenerator;
import io.paymenthighway.json.JsonParser;
import io.paymenthighway.model.request.*;
import io.paymenthighway.model.response.*;
import io.paymenthighway.model.response.transaction.DebitTransactionResponse;
import io.paymenthighway.security.SecureSigner;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PaymentHighway Payment API Connections
 */
public class PaymentAPIConnection implements Closeable {

  /* Payment API headers */
  private static final String USER_AGENT = "PaymentHighway Java Lib";
  private static final String METHOD_POST = "POST";
  private static final String METHOD_GET = "GET";
  private static final String SPH_API_VERSION = "20160630";

  private String serviceUrl = "";
  private String signatureKeyId = null;
  private String signatureSecret = null;
  private String account = null;
  private String merchant = null;
  private boolean checkResponseStatus = false;

  private CloseableHttpClient httpclient;

  /**
   * Constructor
   *
   * @param serviceUrl
   * @param account
   * @param merchant
   * @param signatureKeyId
   * @param signatureSecret
   */
  public PaymentAPIConnection(String serviceUrl, String signatureKeyId, String signatureSecret, String account, String merchant) {
    this.serviceUrl = serviceUrl;
    this.signatureKeyId = signatureKeyId;
    this.signatureSecret = signatureSecret;
    this.account = account;
    this.merchant = merchant;
  }

  /**
   * Set whether or not to ensure that the result code of API responses are "100". By default no checks are made.
   *
   * @param checkResponseStatus If true then the result code of each API response is checked and if
   *                            not "100" then an {@link ErrorResponseException} is thrown.
   */
  public void setCheckResponseStatus(boolean checkResponseStatus) {
    this.checkResponseStatus = checkResponseStatus;
  }

  public void setHttpClient(CloseableHttpClient httpClient) {
    this.httpclient = httpClient;
  }

  public InitTransactionResponse initTransactionHandle() throws IOException {

    final String paymentUri = "/transaction";

    String response = executePost(paymentUri, createNameValuePairs());

    return mapResponse(response, InitTransactionResponse.class);
  }

  public DebitTransactionResponse debitTransaction(UUID transactionId, TransactionRequest request) throws IOException {

    final String paymentUri = "/transaction/";
    final String actionUri = "/debit";
    String debitUri = paymentUri + transactionId + actionUri;

    String response = executePost(debitUri, createNameValuePairs(), request);

    return mapResponse(response, DebitTransactionResponse.class);
  }

  public TransactionResponse creditTransaction(UUID transactionId, TransactionRequest request) throws IOException {

    final String paymentUri = "/transaction/";
    final String actionUri = "/credit";
    String creditUri = paymentUri + transactionId + actionUri;

    String response = executePost(creditUri, createNameValuePairs(), request);

    return mapResponse(response, TransactionResponse.class);
  }

  public TransactionResponse revertTransaction(UUID transactionId, RevertTransactionRequest request) throws IOException {

    final String paymentUri = "/transaction/";
    final String actionUri = "/revert";
    String revertUri = paymentUri + transactionId + actionUri;

    String response = executePost(revertUri, createNameValuePairs(), request);

    return mapResponse(response, TransactionResponse.class);
  }

  public CommitTransactionResponse commitTransaction(UUID transactionId, CommitTransactionRequest request) throws IOException {

    final String paymentUri = "/transaction/";
    final String actionUri = "/commit";
    String commitUri = paymentUri + transactionId + actionUri;

    String response = executePost(commitUri, createNameValuePairs(), request);

    return mapResponse(response, CommitTransactionResponse.class);
  }

  public TransactionResultResponse transactionResult(UUID transactionId) throws IOException {

    final String paymentUri = "/transaction/";
    final String actionUri = "/result";
    String transactionResultUrl = paymentUri + transactionId + actionUri;

    String response = executeGet(transactionResultUrl, createNameValuePairs());

    return mapResponse(response, TransactionResultResponse.class);
  }

  public TransactionStatusResponse transactionStatus(UUID transactionId) throws IOException {

    final String paymentUri = "/transaction/";

    String statusUri = paymentUri + transactionId;

    String response = executeGet(statusUri, createNameValuePairs());

    return mapResponse(response, TransactionStatusResponse.class);
  }

  public OrderSearchResponse searchOrders(String order) throws IOException {

    final String paymentUri = "/transactions/?order=";

    String searchUri = paymentUri + order;

    String response = executeGet(searchUri, createNameValuePairs());

    return mapResponse(response, OrderSearchResponse.class);
  }

  public TokenizationResponse tokenization(UUID tokenizationId) throws IOException {

    final String paymentUri = "/tokenization/";

    String tokenUri = paymentUri + tokenizationId;

    String response = executeGet(tokenUri, createNameValuePairs());

    return mapResponse(response, TokenizationResponse.class);
  }

  public ReportResponse fetchReport(String date) throws IOException {

    final String reportUri = "/report/batch/";

    String fetchUri = reportUri + date;

    String response = executeGet(fetchUri, createNameValuePairs());

    return mapResponse(response, ReportResponse.class);
  }

  public ReconciliationReportResponse fetchReconciliationReport(String date) throws IOException {
    return fetchReconciliationReport(date, false);
  }

  public ReconciliationReportResponse fetchReconciliationReport(String date, Boolean useDateProcessed) throws IOException {
    final String reportUri = "/report/reconciliation/";

    String queryString = String.format("?use-date-processed=%s", useDateProcessed);

    String fetchUri = reportUri + date + queryString;

    String response = executeGet(fetchUri, createNameValuePairs());

    return mapResponse(response, ReconciliationReportResponse.class);
  }

  protected String executeGet(String requestUri, List<NameValuePair> nameValuePairs) throws IOException {
    CloseableHttpClient httpclient = returnHttpClients();

    SecureSigner ss = new SecureSigner(signatureKeyId, signatureSecret);

    HttpRequestBase httpRequest = new HttpGet(serviceUrl + requestUri);

    String signature = createSignature(ss, METHOD_GET, requestUri, nameValuePairs, null);
    nameValuePairs.add(new BasicNameValuePair("signature", signature));

    addHeaders(httpRequest, nameValuePairs);

    ResponseHandler<String> responseHandler = new PaymentHighwayResponseHandler(ss, METHOD_GET, requestUri);

    return httpclient.execute(httpRequest, responseHandler);
  }

  protected String executePost(String requestUri, List<NameValuePair> nameValuePairs, Request requestBody) throws IOException {
    CloseableHttpClient httpclient = returnHttpClients();

    SecureSigner ss = new SecureSigner(signatureKeyId, signatureSecret);

    HttpPost httpRequest = new HttpPost(serviceUrl + requestUri);

    String signature = createSignature(ss, METHOD_POST, requestUri, nameValuePairs, requestBody);
    nameValuePairs.add(new BasicNameValuePair("signature", signature));

    addHeaders(httpRequest, nameValuePairs);

    if (requestBody != null) {
      addBody(httpRequest, requestBody);
    }

    ResponseHandler<String> responseHandler = new PaymentHighwayResponseHandler(ss, METHOD_POST, requestUri);

    return httpclient.execute(httpRequest, responseHandler);
  }

  private String executePost(String requestUri, List<NameValuePair> nameValuePairs) throws IOException {
    return executePost(requestUri, nameValuePairs, null);
  }

  protected void addHeaders(HttpRequestBase httpPost, List<NameValuePair> nameValuePairs) {

    httpPost.addHeader(HTTP.USER_AGENT, USER_AGENT);
    httpPost.addHeader(HTTP.CONTENT_TYPE, "application/json; charset=utf-8");

    for (NameValuePair param : nameValuePairs) {
      httpPost.addHeader(param.getName(), param.getValue());
    }
  }

  private void addBody(HttpPost httpPost, Request request) {
    JsonGenerator jsonGen = new JsonGenerator();
    String requestBody = jsonGen.createTransactionJson(request);
    StringEntity requestEntity = new StringEntity(requestBody, "utf-8");

    httpPost.setEntity(requestEntity);
  }

  private String createSignature(SecureSigner ss, String method, String uri, List<NameValuePair> nameValuePairs, Request request) {
    String json = "";
    if (request != null) {
      JsonGenerator jsonGenerator = new JsonGenerator();
      json = jsonGenerator.createTransactionJson(request);
    }
    return ss.createSignature(method, uri, nameValuePairs, json);
  }

  /**
   * Create name value pairs
   *
   * @return
   */
  private List<NameValuePair> createNameValuePairs() {
    List<NameValuePair> nameValuePairs = new ArrayList<>();
    nameValuePairs.add(new BasicNameValuePair("sph-api-version", SPH_API_VERSION));
    nameValuePairs.add(new BasicNameValuePair("sph-account", account));
    nameValuePairs.add(new BasicNameValuePair("sph-merchant", merchant));
    nameValuePairs.add(new BasicNameValuePair("sph-timestamp", PaymentHighwayUtility.getUtcTimestamp()));
    nameValuePairs.add(new BasicNameValuePair("sph-request-id", PaymentHighwayUtility.createRequestId()));
    return nameValuePairs;
  }

  private CloseableHttpClient returnHttpClients() {
    if (httpclient == null) {
      httpclient = HttpClients.createDefault();
    }
    return httpclient;
  }

  @Override
  public void close() throws IOException {
    if (httpclient != null) {
      httpclient.close();
    }
  }

  /**
   * Parse the specified response JSON string.
   *
   * @param responseString a JSON response.
   * @param clazz the resulting class.
   * @return the parsed results.
   */
  private <T extends Response> T mapResponse(String responseString, Class<T> clazz) throws ErrorResponseException {
    JsonParser jpar = new JsonParser();
    T response = jpar.mapResponse(responseString, clazz);

    if (checkResponseStatus) {
      // verify that the response result has status code 100
      Result result = response.getResult();
      if (result==null || !"100".equals(result.getCode())) {
        throw new ErrorResponseException(result);
      }
    }

    return response;
  }
}
