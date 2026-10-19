package com.portfolio.analyzer.exception;

import com.portfolio.analyzer.common.ApiResponse;
import com.portfolio.analyzer.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFileType(InvalidFileTypeException ex) {
        logger.error("Invalid file type: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.UNSUPPORTED_FILE_TYPE, ex.getMessage());
    }
    
    @ExceptionHandler(FileSizeLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileSizeLimit(FileSizeLimitException ex) {
        logger.error("File size limit exceeded: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.FILE_TOO_LARGE, ex.getMessage());
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        logger.error("Upload size exceeded: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.FILE_TOO_LARGE, "文件大小超过10MB限制");
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.error("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, ex.getMessage());
    }
    
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAIServiceException(AIServiceException ex) {
        logger.error("AI service error: {}", ex.getMessage(), ex);
        
        if (ex.getCause() instanceof TimeoutException || ex.getCause() instanceof SocketTimeoutException) {
            return buildErrorResponse(ErrorCode.MODEL_TIMEOUT, "模型调用超时，请稍后重试");
        }
        
        return buildErrorResponse(ErrorCode.AI_SERVICE_ERROR, ex.getMessage());
    }
    
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleTimeout(TimeoutException ex) {
        logger.error("Request timeout: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.REQUEST_TIMEOUT, "请求超时");
    }
    
    @ExceptionHandler(ModelTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleModelTimeout(ModelTimeoutException ex) {
        logger.error("Model timeout: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.MODEL_TIMEOUT, ex.getMessage());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        logger.error("Validation failed: {}", message);
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, message);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining("; "));
        logger.error("Constraint violation: {}", message);
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, message);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(ErrorCode.INTERNAL_ERROR, "处理请求时发生错误");
    }
    
    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(ErrorCode errorCode, String message) {
        String traceId = MDC.get("traceId");
        ApiResponse<Void> response = ApiResponse.error(errorCode, message, traceId);
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }
}
