package com.portfolio.analyzer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.rag")
@Getter
@Setter
public class RagConfig {
    
    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private int topK = 3;
    private double similarityThreshold = 0.7;
    private int maxIngestSizeMb = 10;
    private int maxPdfPages = 50;
    private String chatModel = "qwen-plus";
    private String vlmModel = "qwen-vl-max";
    private int minTextLengthForOcr = 50;
    private String embeddingModel = "text-embedding-v4";
    private int embeddingDimensions = 1024;
    
    /**
     * HNSW ef_search参数（控制搜索候选集大小）
     * 
     * <p>更大的值提高召回率但降低性能。
     * 默认100，对于过滤查询应适当提高。
     */
    private int hnswEfSearch = 100;

    /**
     * 重排开启时的向量召回条数。关闭重排时不使用，检索仍只取 {@link #topK}。
     */
    private int recallTopN = 20;

    /**
     * 重排序。默认关闭，关闭时检索行为与未接入重排时一致。
     */
    private Rerank rerank = new Rerank();
    
    public long getMaxIngestSizeBytes() {
        return maxIngestSizeMb * 1024L * 1024L;
    }

    @Getter
    @Setter
    public static class Rerank {
        /** 默认关闭。评估通过前不要在生产打开。 */
        private boolean enabled = false;
        /** 目前只有 dashscope。其它值视为不可用并降级。 */
        private String provider = "dashscope";
        /** 百炼文本重排模型。不要使用已下线的 gte-rerank / gte-rerank-v2。 */
        private String model = "qwen3-rerank";
        /** 单次重排调用超时，超时后回退到向量顺序。 */
        private int timeoutMs = 800;
        /** 重排成功后的相关性下限。只作用于重排分数，关闭重排时不生效。 */
        private double minScore = 0.3;
        /**
         * 官方主机名里的域名，不含协议。
         * 百炼文档：{@code https://{workspaceId}.{region}.maas.aliyuncs.com}。
         */
        private String domain = "maas.aliyuncs.com";
        /**
         * 地域。华北2（北京）{@code cn-beijing}，新加坡 {@code ap-southeast-1}，美东 {@code us-east-1}。
         */
        private String region = "cn-beijing";
        /** 业务空间 ID。启用前必须配置，否则官方主机名不完整，调用失败后降级。 */
        private String workspaceId = "";
        /**
         * 非空时作为端点前缀，不再拼接 domain / region。
         * 控制台若仍给出 {@code https://dashscope.aliyuncs.com}，把这个值设成该主机。
         */
        private String baseUrl = "";
    }
}
