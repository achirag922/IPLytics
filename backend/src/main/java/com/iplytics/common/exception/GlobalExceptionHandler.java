package com.iplytics.common.exception;

import com.iplytics.common.web.ApiEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 9457 problem responses for every error: 400 invalid parameters, 404 unknown resource, 422 semantically
 * invalid request, 500 unexpected error. Bodies never contain stack traces, SQL or internal paths; a 500 carries
 * an {@code errorId} that matches the server log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", e.getMessage(), request);
    }

    @ExceptionHandler(UnprocessableRequestException.class)
    public ProblemDetail unprocessable(UnprocessableRequestException e, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable request", e.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request parameters",
                "One or more request parameters are invalid", request);
        problem.setProperty("errors", e.getConstraintViolations().stream()
                .map(v -> error(lastNode(v.getPropertyPath().toString()), v.getMessage())).toList());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unexpected error {} on {} {}", errorId, request.getMethod(), request.getRequestURI(), e);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. Quote errorId " + errorId + " when reporting it.", request);
        problem.setProperty("errorId", errorId);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(error(fieldError.getField(), fieldError.isBindingFailure()
                    ? "invalid value '%s'".formatted(fieldError.getRejectedValue()) : fieldError.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(e -> errors.add(error(e.getObjectName(), e.getDefaultMessage())));
        return badRequest(ex, errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status, WebRequest request) {
        List<Map<String, Object>> errors = new ArrayList<>();
        ex.getAllValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> errors.add(
                error(result.getMethodParameter().getParameterName(), error.getDefaultMessage()))));
        return badRequest(ex, errors, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers, HttpStatusCode status,
                                                                          WebRequest request) {
        String detail = "Required parameter '%s' is missing".formatted(ex.getParameterName());
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        body.setTitle("Invalid request parameters");
        body.setProperty("errors", List.of(error(ex.getParameterName(), detail)));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String parameter = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        Class<?> type = ex.getRequiredType();
        String detail = "Invalid value '%s' for parameter '%s'".formatted(ex.getValue(), parameter);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        body.setTitle("Invalid request parameters");
        if (type != null && type.isEnum() && ApiEnum.class.isAssignableFrom(type)) {
            body.setProperty("allowedValues", ApiEnum.allowed(type));
        }
        body.setProperty("errors", List.of(error(parameter, detail)));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private ResponseEntity<Object> badRequest(Exception ex, List<Map<String, Object>> errors, HttpHeaders headers,
                                              HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "One or more request parameters are invalid");
        body.setTitle("Invalid request parameters");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private static Map<String, Object> error(String parameter, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("parameter", parameter);
        error.put("message", message);
        return error;
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
