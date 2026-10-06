package com.vikisol.arena.common.exception;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.integration.provider.ProviderException;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Single point of enforcement for turning exceptions into a consistent ApiResponse envelope -
// mirrors HRLMS-BE's GlobalExceptionHandler so error shapes stay predictable across both products.
// Every error body is {"success": false, "message": "..."} plus "data" only for field errors.
// The same shape comes from JwtAuthenticationEntryPoint (401), SecurityConfig's access-denied
// handler (403), the security filters' own 403/429 writes, and ApiErrorController for anything
// that reaches the servlet /error page without passing through here.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, ex.getMessage(), null));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBadRequest(BadRequestException ex) {
        Map<String, String> data = ex.getCode() == null ? null : Map.of("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false, ex.getMessage(), data));
    }

    // sign-in itself always forces the generic "Invalid email or password" message deliberately
    // (don't leak which of the two was wrong) - but /auth/refresh and /auth/2fa/verify now throw
    // this same exception type with genuinely useful messages ("Refresh token is invalid or
    // expired," "Incorrect verification code"), so this falls back to ex.getMessage() when one
    // was actually set rather than always overwriting it.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "Invalid email or password";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(false, message, null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(false, "Access denied", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        String readable = errors.entrySet().stream()
                .map(e -> humanizeField(e.getKey()) + " " + e.getValue())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, readable.isBlank() ? "Please check the highlighted fields" : readable, errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("'%s' is not a valid value for %s", ex.getValue(), humanizeField(ex.getName()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false, message, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, "One of the fields you entered isn't in a valid format", null));
    }

    // Spring MVC's own request errors (unknown route, wrong HTTP method, missing parameter or
    // header, unsupported content type, @RequestParam constraint failures, ResponseStatusException).
    // They are ServletExceptions, not RuntimeExceptions, so they used to fall through to
    // handleGeneric as a 500. Each carries its real status; the message stays human.
    @ExceptionHandler({ErrorResponseException.class, NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class, HttpMediaTypeException.class,
            ServletRequestBindingException.class, MissingServletRequestPartException.class,
            AsyncRequestTimeoutException.class})
    public ResponseEntity<ApiResponse<Void>> handleSpringRequestError(Exception ex) {
        HttpStatusCode status = ((ErrorResponse) ex).getStatusCode();
        String message = switch (ex) {
            case MissingServletRequestParameterException m -> humanizeField(m.getParameterName()) + " is required";
            case MissingRequestHeaderException m -> "The " + m.getHeaderName() + " header is required";
            case MissingServletRequestPartException m -> humanizeField(m.getRequestPartName()) + " is required";
            case ResponseStatusException r when r.getReason() != null && !r.getReason().isBlank() -> r.getReason();
            default -> messageFor(status);
        };
        if (status.is5xxServerError()) {
            log.error("Request failed", ex);
        }
        return ResponseEntity.status(status).body(new ApiResponse<>(false, message, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiResponse<>(false, "That file is too large. The limit is 10 MB.", null));
    }

    // @Validated method parameters (outside a request body).
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, message.isBlank() ? "Please check the values you entered" : message, null));
    }

    // An email/SMS/WhatsApp/Teams/OpenAI call failed. The message is the short user-facing text
    // chosen by the kind of failure; the provider's own error was logged, redacted, where it was
    // thrown (ProviderException.failure) and never reaches the response.
    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleProvider(ProviderException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiResponse<>(false, ex.getMessage(), null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String raw = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("Data integrity violation: {}", raw);
        String lower = raw != null ? raw.toLowerCase() : "";
        String message;
        if (lower.contains("email")) {
            message = "This email address is already in use.";
        } else if (lower.contains("unique") || lower.contains("duplicate")) {
            message = "One of the values you entered is already in use elsewhere in the system.";
        } else {
            message = "This request could not be completed because it conflicts with existing data.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(false, message, null));
    }

    // Lock timeouts and deadlocks on the rows this API locks (join capacity, Jenny action approval,
    // unlock credits) - retryable, not the caller's mistake and not a server fault.
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrency(ConcurrencyFailureException ex) {
        log.warn("Concurrent update conflict: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(false, "Someone else changed this at the same moment. Please try again.", null));
    }

    // Any other database failure (connection, timeout, bad SQL) is our fault, not the caller's -
    // it used to fall into handleRuntime as a 400 carrying the driver's message, SQL included.
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex) {
        log.error("Database error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, "Something went wrong on our end. Please try again.", null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.warn("Request rejected: {}", ex.getMessage());
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "This request could not be processed";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false, message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, "Something went wrong on our end. Please try again.", null));
    }

    // Also used by ApiErrorController, so a status reads the same wherever it is produced.
    static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> "Authentication is required to access this resource";
            case 403 -> "Access denied";
            case 404 -> "Not found";
            case 405 -> "This action isn't supported here";
            case 406 -> "This response format isn't available";
            case 413 -> "That upload is too large";
            case 415 -> "This content type isn't supported";
            case 429 -> "Too many requests. Please wait a moment and try again.";
            default -> status.is5xxServerError()
                    ? "Something went wrong on our end. Please try again."
                    : "This request could not be processed";
        };
    }

    private String humanizeField(String field) {
        String spaced = field.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase();
    }
}
