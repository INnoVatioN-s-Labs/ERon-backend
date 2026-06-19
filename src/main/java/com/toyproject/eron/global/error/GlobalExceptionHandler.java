package com.toyproject.eron.global.error;

import java.time.Instant;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.toyproject.eron.erapi.EternalReturnApiException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EternalReturnApiException.class)
    ResponseEntity<ApiErrorResponse> handleEternalReturnApiException(EternalReturnApiException exception) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Class<?> requiredType = exception.getRequiredType();
        String expectedType = requiredType == null ? "unknown" : requiredType.getSimpleName();

        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        "Invalid value '%s' for request parameter '%s'. Expected type: %s."
                                .formatted(exception.getValue(), exception.getName(), expectedType)
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        exception.getMessage()
                ));
    }
}
