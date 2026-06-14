package online.misterpilot.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import online.misterpilot.platform.dto.response.ApiErrorResponse;
import online.misterpilot.platform.dto.response.ApiErrorResponse.FieldErrorDetail;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ──────────────────────────────────────────────
    // 400 — Bad Request
    // ──────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return build(request, HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<FieldErrorDetail> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> FieldErrorDetail.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more fields are invalid")
                .path(request.getRequestURI())
                .errors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return build(request, HttpStatus.BAD_REQUEST, "Bad Request", "Malformed JSON body");
    }

    // ──────────────────────────────────────────────
    // 401 / 403 — Auth
    // ──────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {

        return build(request, HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        return build(request, HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    // ──────────────────────────────────────────────
    // 404 — Not Found
    // ──────────────────────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {

        return build(request, HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    // ──────────────────────────────────────────────
    // 405 — Method Not Allowed
    // ──────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        return build(request, HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ex.getMessage());
    }

    // ──────────────────────────────────────────────
    // 409 — Conflict
    // ──────────────────────────────────────────────

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        return build(request, HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn("Data integrity violation: {}", ex.getMessage());
        return build(request, HttpStatus.CONFLICT,
                "Conflict", "Operation violates a data constraint");
    }

    // ──────────────────────────────────────────────
    // 501 — Not Implemented
    // ──────────────────────────────────────────────

    // ──────────────────────────────────────────────
    // 502 — Bad Gateway (external API failures)
    // ──────────────────────────────────────────────

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpClientError(
            HttpClientErrorException ex,
            HttpServletRequest request) {

        log.warn("External API returned client error: {}", ex.getMessage());
        return build(request, HttpStatus.BAD_GATEWAY,
                "Bad Gateway", "Upstream service rejected the request");
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiErrorResponse> handleRestClient(
            RestClientException ex,
            HttpServletRequest request) {

        log.error("External API call failed: {}", ex.getMessage());
        return build(request, HttpStatus.BAD_GATEWAY,
                "Bad Gateway", "Failed to communicate with upstream service");
    }

    // ──────────────────────────────────────────────
    // 501 — Not Implemented
    // ──────────────────────────────────────────────

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupported(
            UnsupportedOperationException ex,
            HttpServletRequest request) {

        return build(request, HttpStatus.NOT_IMPLEMENTED, "Not Implemented", ex.getMessage());
    }

    // ──────────────────────────────────────────────
    // 500 — Catch-all
    // ──────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAll(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return build(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error", "An unexpected error occurred");
    }

    // ──────────────────────────────────────────────
    // helper
    // ──────────────────────────────────────────────

    private ResponseEntity<ApiErrorResponse> build(
            HttpServletRequest request,
            HttpStatus status,
            String error,
            String message) {

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .build();

        log.debug("Error response — {} {}: {}", status.value(), error, message);
        return ResponseEntity.status(status).body(body);
    }
}
