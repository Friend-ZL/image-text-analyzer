package com.portfolio.analyzer.rerank;

import com.portfolio.analyzer.repository.VectorStorePort;

import java.util.List;

/**
 * 对已经通过租户和 ACL 过滤的候选做重排。
 *
 * <p>实现只能重排传入的候选，不能扩大可见范围，也不能跨租户合并。
 */
public interface RerankerPort {

    /**
     * @param query 用户原问题
     * @param candidates 向量召回结果，调用方保证已按租户和 ACL 过滤
     * @param topK 最多返回的条数
     * @return 按相关性从高到低，长度不超过 topK
     */
    List<Scored> rerank(String query, List<VectorStorePort.SearchResult> candidates, int topK);

    /**
     * @param result 原候选
     * @param score 重排分。Noop 实现使用向量相似度
     */
    record Scored(VectorStorePort.SearchResult result, double score) {}
}
