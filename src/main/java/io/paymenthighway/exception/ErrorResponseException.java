package io.paymenthighway.exception;

import org.apache.http.client.ClientProtocolException;

import io.paymenthighway.model.response.Result;
import io.paymenthighway.model.response.Status;

/**
 * Payment Highway Error Response Exception
 * <p/>
 * Raised when the Payment Highway response result indicates an error
 */
public class ErrorResponseException extends ClientProtocolException {
  private static final long serialVersionUID = 1L;
  private final Result result;

  /**
   * @param result the result (not null).
   */
  public ErrorResponseException(Result result) {
    super(result!=null ? "error code " + result.getCode() + " (" + result.getMessage() + ")" : "");
    if (result==null) throw new NullPointerException();
    this.result = result;
  }

  /**
   * @return the result that caused this exception to get thrown (never null).
   */
  public Result getResult() {
    return result;
  }
}
