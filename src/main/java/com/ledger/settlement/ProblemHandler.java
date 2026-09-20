package com.ledger.settlement;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every failure leaves the API as an RFC 9457 problem detail (application/problem+json). */
@RestControllerAdvice
public class ProblemHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), String.valueOf(error.getDefaultMessage()));
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The request body is invalid", "validation-error", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail missingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Missing parameter", ex.getMessage(), "missing-parameter", request);
    }

    @ExceptionHandler(MerchantNotFoundException.class)
    public ProblemDetail merchantNotFound(MerchantNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Merchant not found", ex.getMessage(), "merchant-not-found", request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail,
                                         String type, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://ledger.example.com/problems/" + type));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail paymentNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Payment not found", ex.getMessage(), "payment-not-found", request);
    }
}
