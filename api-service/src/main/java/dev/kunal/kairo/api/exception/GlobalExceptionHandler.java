package dev.kunal.kairo.api.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.kunal.kairo.common.dto.ErrorResponse;
import dev.kunal.kairo.common.dto.ValidationErrorResponse;
import dev.kunal.kairo.common.exception.ErrorCode;
import dev.kunal.kairo.common.exception.InvalidRequestException;
import dev.kunal.kairo.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        private ErrorResponse buildErrorResponse(String message, ErrorCode errorCode, String path) {
                return new ErrorResponse(
                                LocalDateTime.now().toString(),
                                path,
                                errorCode.name(),
                                message);
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex,
                        HttpServletRequest request) {
                logger.warn("Resource not found: {}", ex.getMessage());
                ErrorResponse errorResponse = buildErrorResponse(ex.getMessage(), ErrorCode.RESOURCE_NOT_FOUND,
                                request.getRequestURI());
                return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }

        @ExceptionHandler(InvalidRequestException.class)
        public ResponseEntity<ErrorResponse> handleInvalidRequestException(InvalidRequestException ex,
                        HttpServletRequest request) {
                logger.warn("Invalid request: {}", ex.getMessage());
                ErrorResponse errorResponse = buildErrorResponse(ex.getMessage(), ex.getErrorCode(),
                                request.getRequestURI());
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                Map<String, String> fieldErrors = new HashMap<>();
                ex.getBindingResult().getFieldErrors()
                                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

                ValidationErrorResponse errorResponse = new ValidationErrorResponse(
                                LocalDateTime.now().toString(),
                                request.getRequestURI(),
                                ErrorCode.VALIDATION_FAILED.name(),
                                "Validation failed for one or more fields",
                                fieldErrors);

                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ErrorResponse> handleTypeMismatch(
                        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,
                        HttpServletRequest request) {
                String message = "Invalid parameter: " + ex.getName();
                ErrorResponse errorResponse = buildErrorResponse(message, ErrorCode.VALIDATION_FAILED,
                                request.getRequestURI());
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
                logger.error("Unexpected error: ", ex);
                ErrorResponse errorResponse = buildErrorResponse("An unexpected error occurred",
                                ErrorCode.INTERNAL_ERROR,
                                request.getRequestURI());
                return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
}
