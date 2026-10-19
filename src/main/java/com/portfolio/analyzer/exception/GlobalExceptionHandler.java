package com.portfolio.analyzer.exception;

import com.portfolio.analyzer.security.TenantMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * @since Stage 1
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 租户信息缺失异常 -> 401 Unauthorized
     * 
     * <p>当请求缺少X-Tenant-Id等必需头时抛出。
     * Backend将在BE-1的error-code系统中重新映射此异常。
     */
    @ExceptionHandler(TenantMissingException.class)
    public ResponseEntity<Map<String, Object>> handleTenantMissing(TenantMissingException ex) {
        logger.warn("Tenant missing: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "TENANT_MISSING", ex.getMessage());
    }
    
    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFileType(InvalidFileTypeException ex) {
        logger.error("Invalid file type: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "无效的文件类型", ex.getMessage());
    }
    
    @ExceptionHandler(FileSizeLimitException.class)
    public ResponseEntity<Map<String, Object>> handleFileSizeLimit(FileSizeLimitException ex) {
        logger.error("File size limit exceeded: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "文件大小超限", ex.getMessage());
    }
    
    @ExceptionHandler(InvalidFileContentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFileContent(InvalidFileContentException ex) {
        logger.error("Invalid file content: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "文件内容无效", ex.getMessage());
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        logger.error("Upload size exceeded: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "上传文件过大", "文件大小超过10MB限制");
    }
    
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestPart(MissingServletRequestPartException ex) {
        logger.error("Missing request part: {}", ex.getRequestPartName());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "缺少必填参数", 
                "缺少必填参数: " + ex.getRequestPartName());
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestParameter(
            MissingServletRequestParameterException ex) {
        logger.error("Missing request parameter: {}", ex.getParameterName());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "缺少必填参数", 
                "缺少必填参数: " + ex.getParameterName());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        logger.error("Validation error: {}", errors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "参数验证失败", errors);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.error("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "参数错误", ex.getMessage());
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        logger.error("Illegal state: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "状态错误", ex.getMessage());
    }
    
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAIServiceException(AIServiceException ex) {
        logger.error("AI service error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "AI服务错误", "模型调用失败，请稍后重试");
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "服务器内部错误", "处理请求时发生错误");
    }
    
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String error, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", status.value());
        response.put("error", error);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}

