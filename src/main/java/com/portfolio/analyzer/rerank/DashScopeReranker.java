package com.portfolio.analyzer.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.repository.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 调用阿里云百炼原生 text-rerank。
 *
 * <p>官方 HTTP（文本排序 API）：{@code POST https://{WorkspaceId}.{region}.{domain}/api/v1/services/rerank/text-rerank/text-rerank}。
 * 请求体是 {@code model + input.query/documents + parameters.top_n}。不传 {@code return_documents}。
 * 响应 {@code output.results[]} 按相关性从高到低排列，用 {@code index} 映射回原候选，用 {@code relevance_score} 作为分数。
 * 模型固定走 qwen3-rerank 这一代；gte-rerank 已于 2026-05-30 下线，gte-rerank-v2 不再使用。
 */
@Component
public class DashScopeReranker implements RerankerPort {

    static final String RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";
    /** qwen3-rerank 单次最多 500 篇。 */
    static final int MAX_DOCUMENTS = 500;

    private static final Logger logger = LoggerFactory.getLogger(DashScopeReranker.class);

    private final RagConfig ragConfig;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final HttpClient httpClient;

    @Autowired
    public DashScopeReranker(
            RagConfig ragConfig,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this(ragConfig, objectMapper, apiKey, httpClient(ragConfig));
    }

    public DashScopeReranker(
            RagConfig ragConfig,
            ObjectMapper objectMapper,
            String apiKey,
            HttpClient httpClient) {
        this.ragConfig = ragConfig;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.httpClient = httpClient;
    }

    /**
     * 拼出原生 text-rerank 的 URI。{@code baseUrl} 非空时不再拼域名和地域。
     */
    public static URI resolveEndpoint(RagConfig.Rerank rerank) {
        if (rerank == null) {
            throw new IllegalArgumentException("rerank config is required");
        }
        String explicit = trimToEmpty(rerank.getBaseUrl());
        String root;
        if (!explicit.isEmpty()) {
            root = stripTrailingSlash(explicit);
        } else {
            String domain = hostPart(firstNonBlank(rerank.getDomain(), "maas.aliyuncs.com"));
            String region = firstNonBlank(rerank.getRegion(), "cn-beijing");
            String workspace = trimToEmpty(rerank.getWorkspaceId());
            String host = workspace.isEmpty() ? region + "." + domain : workspace + "." + region + "." + domain;
            root = "https://" + host;
        }
        if (root.endsWith(RERANK_PATH)) {
            return URI.create(root);
        }
        return URI.create(root + RERANK_PATH);
    }

    private static HttpClient httpClient(RagConfig ragConfig) {
        int timeoutMs = 800;
        if (ragConfig.getRerank() != null && ragConfig.getRerank().getTimeoutMs() > 0) {
            timeoutMs = ragConfig.getRerank().getTimeoutMs();
        }
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public List<Scored> rerank(String query, List<VectorStorePort.SearchResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        try {
            return doRerank(query, candidates, topK);
        } catch (RerankFailedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RerankFailedException("rerank call failed", ex);
        }
    }

    private List<Scored> doRerank(String query, List<VectorStorePort.SearchResult> candidates, int topK)
            throws IOException, InterruptedException {
        if (query == null || query.isBlank()) {
            throw new RerankFailedException("rerank query is blank");
        }
        if (apiKey.isEmpty()) {
            throw new RerankFailedException("missing dashscope api key");
        }
        RagConfig.Rerank settings = ragConfig.getRerank();
        if (settings == null) {
            throw new RerankFailedException("rerank config is missing");
        }
        String model = firstNonBlank(settings.getModel(), "qwen3-rerank");
        if (model.toLowerCase(Locale.ROOT).startsWith("gte-rerank")) {
            throw new RerankFailedException("refusing retired rerank model");
        }

        List<VectorStorePort.SearchResult> pool = candidates;
        if (pool.size() > MAX_DOCUMENTS) {
            logger.warn("Truncating rerank candidates from {} to {}", pool.size(), MAX_DOCUMENTS);
            pool = pool.subList(0, MAX_DOCUMENTS);
        }

        int timeoutMs = settings.getTimeoutMs() > 0 ? settings.getTimeoutMs() : 800;
        URI endpoint = resolveEndpoint(settings);
        String payload = objectMapper.writeValueAsString(requestBody(model, query, pool, topK));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        logger.info("Calling DashScope rerank: model={}, documents={}, topN={}, timeoutMs={}",
                model, pool.size(), Math.min(topK, pool.size()), timeoutMs);

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            throw new RerankFailedException("rerank timeout", ex);
        }

        String body = response.body() == null ? "" : response.body();
        int status = response.statusCode();
        String requestId = requestId(body);
        if (status < 200 || status >= 300) {
            logger.warn("DashScope rerank failed: status={}, requestId={}", status, requestId);
            throw new RerankFailedException("rerank http " + status);
        }

        List<Scored> mapped = mapResults(body, pool, topK);
        logger.info("DashScope rerank finished: requestId={}, resultCount={}, scores={}",
                requestId, mapped.size(), scoreSummary(mapped));
        return mapped;
    }

    private Map<String, Object> requestBody(
            String model, String query, List<VectorStorePort.SearchResult> pool, int topK) {
        List<String> documents = new ArrayList<>(pool.size());
        for (VectorStorePort.SearchResult candidate : pool) {
            documents.add(candidate.content() == null ? "" : candidate.content());
        }
        int topN = Math.min(topK, documents.size());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("top_n", topN);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input);
        body.put("parameters", parameters);
        return body;
    }

    private List<Scored> mapResults(String body, List<VectorStorePort.SearchResult> pool, int topK) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new RerankFailedException("rerank response parse failed", ex);
        }
        if (root == null || !root.isObject()) {
            throw new RerankFailedException("rerank response parse failed");
        }
        JsonNode results = root.path("output").path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new RerankFailedException("rerank response missing output.results");
        }

        List<Scored> mapped = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (JsonNode item : results) {
            JsonNode indexNode = item.get("index");
            JsonNode scoreNode = item.get("relevance_score");
            if (indexNode == null || !indexNode.isIntegralNumber() || scoreNode == null || !scoreNode.isNumber()) {
                throw new RerankFailedException("rerank result missing index or relevance_score");
            }
            int index = indexNode.asInt();
            if (index < 0 || index >= pool.size() || !seen.add(index)) {
                throw new RerankFailedException("rerank result index cannot be mapped");
            }
            double score = scoreNode.asDouble();
            if (!Double.isFinite(score)) {
                throw new RerankFailedException("rerank relevance_score is not finite");
            }
            mapped.add(new Scored(pool.get(index), score));
            if (mapped.size() >= topK) {
                break;
            }
        }
        return mapped;
    }

    private String requestId(String body) {
        try {
            JsonNode id = objectMapper.readTree(body).get("request_id");
            if (id != null && id.isTextual()) {
                return id.asText();
            }
        } catch (Exception ignored) {
            // 失败响应不一定是 JSON。调用方只记录状态码。
        }
        return null;
    }

    private static String scoreSummary(List<Scored> mapped) {
        StringBuilder summary = new StringBuilder();
        for (Scored scored : mapped) {
            if (summary.length() > 0) {
                summary.append(',');
            }
            summary.append(scored.result().id()).append('=').append(scored.score());
        }
        return summary.toString();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String value, String fallback) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String stripTrailingSlash(String value) {
        String current = value;
        while (current.endsWith("/")) {
            current = current.substring(0, current.length() - 1);
        }
        return current;
    }

    private static String hostPart(String domain) {
        String value = trimToEmpty(domain);
        if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
