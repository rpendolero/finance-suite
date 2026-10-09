package com.finance.server.infrastructure.adapter.in.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import jakarta.persistence.PersistenceException;
import com.finance.server.infrastructure.adapter.out.persistence.DatabaseFailure;
import org.slf4j.MDC;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  public ProblemDetail badRequest(Exception e) {
    log.warn("Request rejected: errorType={}", e.getClass().getSimpleName());
    var p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            e instanceof IllegalArgumentException ? e.getMessage() : "Petición inválida");
    p.setTitle("Datos inválidos");
    return p;
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail oversizedUpload(MaxUploadSizeExceededException error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Máximo 10 MB por fichero");
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail unavailable(IllegalStateException e) {
    log.warn("Request rejected: errorType={}", e.getClass().getSimpleName());
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class, PersistenceException.class})
  public ProblemDetail persistence(Exception error) {
    var failure = DatabaseFailure.from(error);
    log.error("Database request failed: code={}, errorType={}, sqlState={}, vendorCode={}, correlationId={}",
        failure.code(), error.getClass().getSimpleName(), failure.sqlState(), failure.vendorCode(),
        MDC.get("correlationId"), error);
    String detail = switch (failure.status()) {
      case 409 -> "Conflicto de datos o actualización concurrente. Revisa identificadores y relaciones.";
      case 503 -> "Base de datos temporalmente no disponible. La operación no se ha confirmado.";
      default -> "Error de base de datos. Consulta las trazas con el identificador de correlación.";
    };
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(failure.status()), detail);
    problem.setTitle("Error de persistencia");
    problem.setProperty("code", failure.code());
    String correlationId = MDC.get("correlationId");
    if (correlationId != null) problem.setProperty("correlationId", correlationId);
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail unexpected(Exception e) {
    log.error("Request failed: errorType={}, correlationId={}", e.getClass().getSimpleName(), MDC.get("correlationId"), e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Error interno; no se exponen datos bancarios ni detalles de sesión.");
  }
}
