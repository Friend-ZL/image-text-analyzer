package com.portfolio.analyzer.rerank;

import com.portfolio.analyzer.repository.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 按向量相似度取前 topK。关闭重排，以及远程重排降级时使用。
 */
@Component
public class NoopReranker implements RerankerPort {

    private static final Logger logger = LoggerFactory.getLogger(NoopReranker.class);

    @Override
    public List<Scored> rerank(String query, List<VectorStorePort.SearchResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        logger.debug("Noop rerank: queryLength={}, candidates={}, topK={}",
                query == null ? 0 : query.length(), candidates.size(), topK);
        return candidates.stream()
                .sorted(Comparator.comparingDouble(VectorStorePort.SearchResult::similarity).reversed())
                .limit(topK)
                .map(result -> new Scored(result, result.similarity()))
                .toList();
    }
}
