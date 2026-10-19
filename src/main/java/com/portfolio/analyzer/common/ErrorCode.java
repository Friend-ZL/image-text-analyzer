package com.portfolio.analyzer.common;

import org.springframework.http.HttpStatus;

/**
 * 错误码枚举
 * <p>
 * 错误码格式：E + HTTP状态码 + 两位序号
 * 例如：E40001 表示 400 错误的第 01 号
 * <p>
 * 重要规则：错误码一旦发布不得修改含义、不得复用。新增错误码应按分区顺序添加。
 */
public enum ErrorCode {
    
    // ========== 成功 ==========
    SUCCESS("00000", "成功", HttpStatus.OK),
    
    // ========== 4xx 客户端错误 ==========
    
    // 通用参数校验 (E400xx)
    INVALID_PARAMETER("E40001", "参数校验失败", HttpStatus.BAD_REQUEST),
    FILE_EMPTY("E40002", "文件不能为空", HttpStatus.BAD_REQUEST),
    QUESTION_EMPTY("E40003", "问题不能为空", HttpStatus.BAD_REQUEST),
    // E40004~E40009 预留：参数校验相关
    
    // RAG/向量存储相关 (E401xx)
    // E40100~E40119 预留：RAG 查询、向量检索相关错误，由后续 PR 填充
    
    // 鉴权相关 (E403xx)
    // E40300~E40319 预留：鉴权、权限相关错误，由后续 PR 填充
    
    // 文件大小 (E413xx)
    FILE_TOO_LARGE("E41301", "文件大小超过限制", HttpStatus.PAYLOAD_TOO_LARGE),
    // E41302~E41309 预留：文件大小相关
    
    // 文件类型 (E415xx)
    UNSUPPORTED_FILE_TYPE("E41501", "不支持的文件类型", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    // E41502~E41509 预留：媒体类型相关
    
    // 限流 (E429xx)
    TOO_MANY_REQUESTS("E42901", "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    // E42902~E42909 预留：限流相关
    
    // ========== 5xx 服务端错误 ==========
    
    // 通用服务器错误 (E500xx)
    INTERNAL_ERROR("E50001", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_SERVICE_ERROR("E50002", "AI服务调用失败", HttpStatus.INTERNAL_SERVER_ERROR),
    // E50003~E50009 预留：内部错误相关
    
    // 存储服务相关 (E501xx)
    // E50100~E50119 预留：文件存储、向量存储等服务异常，由后续 PR 填充
    
    // 上游服务异常 (E502xx)
    MODEL_ERROR("E50201", "模型服务异常", HttpStatus.BAD_GATEWAY),
    // E50202~E50209 预留：上游服务相关
    
    // 网关超时 (E504xx)
    MODEL_TIMEOUT("E50401", "模型调用超时", HttpStatus.GATEWAY_TIMEOUT),
    // E50402~E50409 预留：网关超时相关
    
    // 工具调用相关 (E505xx)
    // E50500~E50519 预留：OCR、表格提取等工具调用错误，由后续 PR 填充
    ;
    
    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
    
    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
