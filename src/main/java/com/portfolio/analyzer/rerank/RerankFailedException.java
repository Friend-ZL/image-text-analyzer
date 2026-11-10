package com.portfolio.analyzer.rerank;

/**
 * 远程重排不可用。调用方应回退到向量相似度顺序，而不是让查询失败。
 */
public class RerankFailedException extends RuntimeException {

    public RerankFailedException(String message) {
        super(message);
    }

    public RerankFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
