package io.paymenthighway;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.*;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * General helper methods.
 */
public class PaymentHighwayUtility {

  /**
   * Cryptographically strong pseudo random number generator.
   *
   * @return String UUID.
   */
  public static String createRequestId() {
    return java.util.UUID.randomUUID().toString();
  }

  /**
   * Request timestamp in ISO 8601 combined date and time in UTC.
   *
   * @return String timestamp Example: 2014-09-18T10:32:59Z
   */
  public static String getUtcTimestamp() {
    SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    timeFormatter.setTimeZone(new SimpleTimeZone(SimpleTimeZone.UTC_TIME, "UTC"));
    return timeFormatter.format(new Date());
  }

  /**
   * Convert map to list of name value pairs.
   * @param map
   * @return a list with the specified key-value pairs.
   */
  public static List<NameValuePair> mapToList(final Map<String,String> map) {
    List<NameValuePair> pairs=new ArrayList<>();
    for (Map.Entry<String,String> entry : map.entrySet()) {
      pairs.add(new BasicNameValuePair(entry.getKey(),entry.getValue()));
    }
    return pairs;
  }

  /**
   * Convert a request map to list of name value pairs.
   * @param map a request parameter map. Can be a map of string to string, or a direct http request map (string to string array).
   * @return a list with the specified key-value pairs.
   */
  public static List<NameValuePair> requestMapToList(final Map<String, ? extends Object> map) {
    List<NameValuePair> pairs=new ArrayList<>();
    for (Map.Entry<String, ? extends Object> entry : map.entrySet()) {
      pairs.add(new BasicNameValuePair(entry.getKey(), unboxRequestValue(entry.getValue())));
    }
    return pairs;
  }

  /**
   * Unbox a value that's either a string or an entry straight from an http request (and thus an array).
   * @param value either a string or an array of strings (in which case only the 1st is used)
   * @return the specified value turned into a string
   */
  private static String unboxRequestValue(Object value) {
    if (value == null) return null;

    // unbox the 1st value if it's an array
    if (value.getClass().isArray()) {
      if (Array.getLength(value) == 0) return null;
      value = Array.get(value, 0);
    }

    return value.toString();
  }

  /**
   * Read properties from file
   *
   * @return Properties
   * @throws IOException
   */
  public static Properties getProperties() throws IOException {

    Properties props = new Properties();
    BufferedReader br = null;
    String propFilename = "config.properties";

    try {
      br = new BufferedReader(new FileReader(propFilename));
    } catch (FileNotFoundException ex) {
      // Failed to find the file,
      // so lets try to find the file from resources
      InputStream file = ClassLoader.getSystemResourceAsStream(propFilename);
      try {
        br = new BufferedReader(new InputStreamReader(file, "UTF-8"));
      } catch (Exception e) {
        System.err.println("Could not find property File.");
        e.printStackTrace();
      }
    }

    if (br != null) {
      try {
        props.load(br);
      } catch (IOException ex) {
        System.err.println("Property file reading error.");
        ex.printStackTrace();
      }
    }
    return props;
  }
}
