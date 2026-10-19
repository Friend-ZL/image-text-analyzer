package com.portfolio.analyzer.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应体
 *
 * @param <T> 数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private String code;
    private String message;
    private T data;
    private String traceId;
    
    public ApiResponse() {
    }
    
    public ApiResponse(String code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }
    
    public static <T> ApiResponse<T> success(T data) {
        return success(data, null);
    }
    
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(
            ErrorCode.SUCCESS.getCode(),
            ErrorCode.SUCCESS.getMessage(),
            data,
            traceId
        );
    }
    
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage(), null);
    }
    
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return error(errorCode, message, null);
    }
    
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, String traceId) {
        return new ApiResponse<>(
            errorCode.getCode(),
            message,
            null,
            traceId
        );
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
