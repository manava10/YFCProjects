package com.yfctransport.platform;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalid(IllegalArgumentException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Invalid input: "
            + ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .toList());
  }

  @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
  ProblemDetail concurrentUpdate() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Record changed concurrently. Reload it before retrying.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflict() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "The record conflicts with existing data or database constraints.");
  }
}
