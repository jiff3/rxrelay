package dev.rxrelay.core.api;

import dev.rxrelay.core.service.ConflictException;
import dev.rxrelay.core.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiModels.ErrorResponse> notFound(
      NotFoundException exception, HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), List.of(), request);
  }

  @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
  ResponseEntity<ApiModels.ErrorResponse> conflict(
      RuntimeException exception, HttpServletRequest request) {
    String message =
        exception instanceof ConflictException
            ? exception.getMessage()
            : "Resource conflicts with existing data";
    return response(HttpStatus.CONFLICT, "conflict", message, List.of(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiModels.ErrorResponse> bodyValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<ApiModels.FieldViolation> violations =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ApiModels.FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList();
    return response(
        HttpStatus.BAD_REQUEST,
        "validation_failed",
        "Request validation failed",
        violations,
        request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiModels.ErrorResponse> parameterValidation(
      ConstraintViolationException exception, HttpServletRequest request) {
    List<ApiModels.FieldViolation> violations =
        exception.getConstraintViolations().stream()
            .map(
                value ->
                    new ApiModels.FieldViolation(
                        value.getPropertyPath().toString(), value.getMessage()))
            .toList();
    return response(
        HttpStatus.BAD_REQUEST,
        "validation_failed",
        "Request validation failed",
        violations,
        request);
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ApiModels.ErrorResponse> badRequest(
      Exception exception, HttpServletRequest request) {
    String message =
        exception instanceof IllegalArgumentException
            ? exception.getMessage()
            : "Request contains an invalid value";
    return response(HttpStatus.BAD_REQUEST, "invalid_request", message, List.of(), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiModels.ErrorResponse> unexpected(
      Exception exception, HttpServletRequest request) {
    log.error("Unhandled API exception", exception);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal_error",
        "The request could not be completed",
        List.of(),
        request);
  }

  private ResponseEntity<ApiModels.ErrorResponse> response(
      HttpStatus status,
      String code,
      String message,
      List<ApiModels.FieldViolation> violations,
      HttpServletRequest request) {
    Object attribute = request.getAttribute(CorrelationIdFilter.HEADER);
    String requestId =
        attribute == null ? request.getHeader(CorrelationIdFilter.HEADER) : attribute.toString();
    return ResponseEntity.status(status)
        .body(new ApiModels.ErrorResponse(code, message, requestId, Instant.now(), violations));
  }
}
