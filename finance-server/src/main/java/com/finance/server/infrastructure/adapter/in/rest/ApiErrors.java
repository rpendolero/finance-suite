package com.finance.server.infrastructure.adapter.in.rest;

import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@lombok.extern.slf4j.Slf4j
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

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail unavailable(IllegalStateException e) {
    log.warn("Request rejected: errorType={}", e.getClass().getSimpleName());
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(DataAccessException.class)
  public ProblemDetail persistence(DataAccessException e) {
    log.warn("Request rejected: errorType={}", e.getClass().getSimpleName());
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "Conflicto de persistencia. Revisa identificadores y relaciones de productos.");
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail unexpected(Exception e) {
    log.error("Request failed: errorType={}", e.getClass().getSimpleName());
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Error interno; no se exponen datos bancarios ni detalles de sesión.");
  }
}
