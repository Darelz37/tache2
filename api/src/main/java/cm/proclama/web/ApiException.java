package cm.proclama.web;

import java.util.Map;

public class ApiException extends RuntimeException {
  public final int status;
  public final String code;
  public final Map<String, Object> extra;
  public final Map<String, String> headers;

  public ApiException(int status, String code, String message) {
    this(status, code, message, Map.of(), Map.of());
  }

  public ApiException(int status, String code, String message, Map<String, Object> extra, Map<String, String> headers) {
    super(message);
    this.status = status;
    this.code = code;
    this.extra = extra;
    this.headers = headers;
  }
}
