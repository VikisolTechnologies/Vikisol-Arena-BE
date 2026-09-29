package com.vikisol.arena.common.exception;

import com.vikisol.arena.common.dto.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Replaces Spring Boot's BasicErrorController, whose {timestamp, status, error, path} body was the
// one error shape left that isn't ApiResponse. Reached only for errors that never pass through
// GlobalExceptionHandler: a filter throwing, a container-level sendError, a request rejected
// before dispatch. Only the status is echoed - never the exception message or path.
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatusCode status = code instanceof Integer i && i >= 400 && i < 600
                ? HttpStatusCode.valueOf(i) : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(false, GlobalExceptionHandler.messageFor(status), null));
    }
}
