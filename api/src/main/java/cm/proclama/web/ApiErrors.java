package cm.proclama.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Format d'erreur unique : {"error":{"code":"...","message":"..."}}. */
@RestControllerAdvice
public class ApiErrors {
  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  private static ResponseEntity<Map<String, Object>> build(int status, String code, String msg, Map<String, Object> extra,
      Map<String, String> headers) {
    Map<String, Object> err = new LinkedHashMap<>();
    err.put("code", code);
    err.put("message", msg);
    err.putAll(extra);
    HttpHeaders h = new HttpHeaders();
    headers.forEach(h::set);
    return ResponseEntity.status(status).headers(h).contentType(MediaType.APPLICATION_JSON).body(M.of("error", err));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, Object>> api(ApiException e) {
    return build(e.status, e.code, e.getMessage(), e.extra, e.headers);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
    return build(413, "too_large", "Fichier trop volumineux (10 Mo maximum).", Map.of(), Map.of());
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
      MissingServletRequestPartException.class, MissingServletRequestParameterException.class, MultipartException.class,
      HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
  public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
    return build(400, "bad_request", "Requete invalide.", Map.of(), Map.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e) {
    return build(404, "not_found", "Ressource introuvable.", Map.of(), Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> other(Exception e) {
    log.error("erreur non geree", e);
    return build(500, "internal_error", "Erreur interne.", Map.of(), Map.of());
  }
}
