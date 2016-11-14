/**
 *
 */
package io.paymenthighway.security;

import io.paymenthighway.PaymentHighwayUtility;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Creates a signature for PaymentHighway messages
 */
public class SecureSigner {

  private static final String SignatureScheme = "SPH1";
  private static final String Algorithm = "HmacSHA256";

  private static final Comparator<NameValuePair> NAME_COMPARATOR = new Comparator<NameValuePair>() {
    @Override
    public int compare(NameValuePair p1, NameValuePair p2) {
      return p1.getName().compareTo(p2.getName());
    }
  };

  private String secretKeyId = null;
  private String secretKey = null;

  private SecretKeySpec secretKeySpec = null;

  /**
   * Constructor
   *
   * @param id the key name.
   * @param key the secret key.
   */
  public SecureSigner(String id, String key) {
    this.secretKeyId = id;
    this.secretKey = key;
    this.secretKeySpec = initSecretKeySpec();
  }

  private SecretKeySpec initSecretKeySpec() {

      SecretKeySpec keySpec = null;

      try {
          keySpec = new SecretKeySpec(this.secretKey.getBytes("UTF-8"), Algorithm);
      } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
      }

      return keySpec;
  }

  /**
   * Init signer
   *
   * @return a new initialized javax.crypto.Mac instance
   */
  private Mac initSigner() {
    Mac signer = null;

    try {
      signer = Mac.getInstance(Algorithm);
      signer.init(secretKeySpec);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      e.printStackTrace();
    }

    return signer;
  }

  /**
   * Create signature
   *
   * @param method
   * @param uri
   * @param body
   * @return eg:
   * "SPH1 testKey 51dcbaf5a9323daed24c0cdc5bb5d344f321aa84435b64e5da3d8f6c49370532"
   */
  public String createSignature(String method, String uri, Map<String, String> keyValues, String body) {
    return createSignature(method, uri, PaymentHighwayUtility.mapToList(keyValues), body);
  }

  /**
   * Create signature
   *
   * @param method
   * @param uri
   * @param body
   * @return eg:
   * "SPH1 testKey 51dcbaf5a9323daed24c0cdc5bb5d344f321aa84435b64e5da3d8f6c49370532"
   */
  public String createSignature(String method, String uri, List<NameValuePair> keyValues, String body) {
    return String.format("%s %s %s", SignatureScheme, secretKeyId, sign(method, uri, keyValues, body));
  }

  /**
   * Create signature String from the actual parameters
   *
   * @param method
   * @param uri
   * @param body
   * @return the signature
   */
  private String sign(String method, String uri, List<NameValuePair> keyValues, String body) {
    List<NameValuePair> sphKeyValues = getSphParameters(keyValues);
    Collections.sort(sphKeyValues, NAME_COMPARATOR);
    String stringToSign = String.format("%s\n%s\n%s\n%s", method, uri, concatenateKeyValues(sphKeyValues), body.trim());

    byte[] signature = null;
    try {
      signature = initSigner().doFinal(stringToSign.getBytes("UTF-8"));
    } catch (IllegalStateException | UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    return toLowerCase(DatatypeConverter.printHexBinary(signature));
  }

  private static String toLowerCase(String s) {
    // specify the locale instead of hoping the user's default locale will be ok
    return s == null ? null : s.toLowerCase(Locale.US);
  }

  /**
   * Signature is formed from parameters that start with "sph-". Therefore we
   * return only those parameters of the specified signature param set.
   *
   * @param map that may include all params
   * @return a new list with only params starting "sph-"
   */
  private static List<NameValuePair> getSphParameters(Collection<NameValuePair> map) {
    List<NameValuePair> sphs = new ArrayList<>(map.size());
    for (NameValuePair e : map) {
      if (toLowerCase(e.getName()).startsWith("sph-")) {
        sphs.add(e);
      }
    }
    return sphs;
  }

  /**
   * Concanate key values into key:param\n String
   *
   * @param keyValues
   * @return
   */
  private String concatenateKeyValues(List<NameValuePair> keyValues) {
    StringBuilder buf = new StringBuilder(128);

    for (NameValuePair entry : keyValues) {
      // separating entries by \n
      if (buf.length() > 0) buf.append('\n');
      buf.append(toLowerCase(entry.getName())).append(':').append(entry.getValue());
    }

    return buf.toString();
  }

  /**
   * Validates the response redirection by checking the provided signature against the calculated one.
   * @param keyValues The request parameters from the redirection. Can be a map of string to string, or a direct http request map (string to string array).
   * @return
   */
  public boolean validateFormRedirect(Map<String, ? extends Object> keyValues) {
    return validateFormRedirect(PaymentHighwayUtility.requestMapToList(keyValues));
  }

  /**
   * Validates the response redirection by checking the provided signature against the calculated one.
   * @param keyValues The request parameters from the redirection
   * @return
   */
  public boolean validateFormRedirect(List<NameValuePair> keyValues) {
    return validateSignature("GET", "", keyValues, "");
  }

  /**
   * Validates the response by checking the provided signature against the calculated one.
   *
   * @param method HTTP METHOD e.g. POST or GET
   * @param uri The request URI
   * @param keyValues The key value pairs of headers or request parameters
   * @param content The body content
   * @return true if signature is found and matches the calculated one
   */
  public boolean validateSignature(String method, String uri, Map<String, String> keyValues, String content) {
    List<NameValuePair> keyValuesList = PaymentHighwayUtility.mapToList(keyValues);
    return validateSignature(method, uri, keyValuesList, content);
  }

  /**
   * Validates the response by checking the provided signature against the calculated one.
   *
   * @param method HTTP METHOD e.g. POST or GET
   * @param uri The request URI
   * @param response The key value pairs of headers
   * @param content The body content
   * @return true if signature is found and matches the calculated one
   */
  public boolean validateSignature(String method, String uri, HttpResponse response, String content) {
    List<NameValuePair> nameValuePairs = this.getHeadersAsNameValuePairs(response.getAllHeaders());
    return validateSignature(method, uri, nameValuePairs, content);
  }

  /**
   * Validates the response by checking the provided signature against the calculated one.
   *
   * @param method HTTP METHOD e.g. POST or GET
   * @param uri The request URI
   * @param keyValues The key value pairs of headers or request parameters
   * @param content The body content
   * @return true if signature is found and matches the calculated one
   */
  public boolean validateSignature(String method, String uri, List<NameValuePair> keyValues, String content) {

    String receivedSignature = findSignature(keyValues);

    if (receivedSignature.isEmpty()) {
      return false;
    } else {
      String createdSignature = createSignature(method, uri, keyValues, content);
      return receivedSignature.equals(createdSignature);
    }
  }

  private String findSignature(List<NameValuePair> nameValuePairs) {
    for (NameValuePair entry : nameValuePairs) {
      if (toLowerCase(entry.getName()).equals("signature")) {
        return entry.getValue();
      }
    }
    return "";
  }

  private List<NameValuePair> getHeadersAsNameValuePairs(Header[] headers) {
    ArrayList<NameValuePair> list = new ArrayList<>();
    for (Header header : headers) {
      NameValuePair nameValuePair = new BasicNameValuePair(header.getName(), header.getValue());
      list.add(nameValuePair);
    }
    return list;
  }
}
