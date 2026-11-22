package com.portfolio.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.RagQueryResponse;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.rerank.DashScopeReranker;
import com.portfolio.analyzer.rerank.NoopReranker;
import com.portfolio.analyzer.security.TenantPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagQueryRerankTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;
    private VectorStorePort vectorStore;
    private MultimodalAIClient aiClient;
    private MeterRegistry meterRegistry;
    private RagConfig ragConfig;
    private RagQueryService service;
    private final TenantPrincipal principal = TenantPrincipal.of("tenant-a", "user-a");

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        vectorStore = mock(VectorStorePort.class);
        aiClient = mock(MultimodalAIClient.class);
        when(aiClient.chat(anyString())).thenReturn("answer");

        var embeddingModel = mock(org.springframework.ai.embedding.EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f, 0f, 0f});

        ragConfig = new RagConfig();
        ragConfig.setTopK(2);
        ragConfig.setRecallTopN(20);
        ragConfig.setSimilarityThreshold(0.7);
        ragConfig.setEmbeddingDimensions(4);
        ragConfig.setChatModel("qwen-plus");
        ragConfig.getRerank().setEnabled(true);
        ragConfig.getRerank().setModel("qwen3-rerank");
        ragConfig.getRerank().setTimeoutMs(500);
        ragConfig.getRerank().setMinScore(0.3);
        ragConfig.getRerank().setBaseUrl(server.url("/").toString());

        meterRegistry = new SimpleMeterRegistry();
        DashScopeReranker reranker = new DashScopeReranker(ragConfig, objectMapper, "test-key");
        service = new RagQueryService(
                vectorStore,
                embeddingModel,
                aiClient,
                ragConfig,
                new NoopReranker(),
                reranker,
                meterRegistry);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
    }

    @Test
    void mapsResultIndexBackToCandidates() throws Exception {
        VectorStorePort.SearchResult first = hit("alpha", 0.1);
        VectorStorePort.SearchResult second = hit("beta", 0.2);
        VectorStorePort.SearchResult third = hit("gamma", 0.3);
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(first, second, third));
        server.enqueue(json(200, """
                {"request_id":"req-1","output":{"results":[
                  {"index":2,"relevance_score":0.91},
                  {"index":0,"relevance_score":0.62}
                ]}}
                """));

        RagQueryResponse response = service.queryWithContext(principal, "什么是排序");

        assertThat(response.isReranked()).isTrue();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("gamma", "alpha");
        assertThat(response.getAnswer()).isEqualTo("answer");
        assertThat(response.getModel()).isEqualTo("qwen-plus");
        assertThat(fallbackCount()).isZero();

        org.mockito.ArgumentCaptor<Integer> limit = org.mockito.ArgumentCaptor.forClass(Integer.class);
        org.mockito.ArgumentCaptor<Double> threshold = org.mockito.ArgumentCaptor.forClass(Double.class);
        verify(vectorStore).searchSimilar(any(), any(float[].class), limit.capture(), threshold.capture());
        assertThat(limit.getValue()).isEqualTo(20);
        assertThat(threshold.getValue()).isEqualTo(0.7);

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/api/v1/services/rerank/text-rerank/text-rerank");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(body.get("model").asText()).isEqualTo("qwen3-rerank");
        assertThat(body.get("input").get("query").asText()).isEqualTo("什么是排序");
        assertThat(body.get("input").get("documents")).extracting(JsonNode::asText)
                .containsExactly("alpha", "beta", "gamma");
        assertThat(body.get("parameters").get("top_n").asInt()).isEqualTo(2);
        assertThat(body.get("parameters").has("return_documents")).isFalse();
        assertThat(body.toString()).doesNotContain("gte-rerank");

        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(aiClient).chat(prompt.capture());
        assertThat(prompt.getValue()).contains("gamma");
        assertThat(prompt.getValue().indexOf("gamma")).isLessThan(prompt.getValue().indexOf("alpha"));
    }

    @Test
    void dropsCandidatesBelowMinScore() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("kept-doc", 0.2), hit("dropped-doc", 0.3)));
        server.enqueue(json(200, """
                {"output":{"results":[
                  {"index":0,"relevance_score":0.95},
                  {"index":1,"relevance_score":0.2}
                ]}}
                """));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isTrue();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("kept-doc");
        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(aiClient).chat(prompt.capture());
        assertThat(prompt.getValue()).contains("kept-doc");
        assertThat(prompt.getValue()).doesNotContain("dropped-doc");
    }

    @Test
    void keepsScoreEqualToMinScore() {
        ragConfig.getRerank().setMinScore(0.3);
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("boundary", 0.2)));
        server.enqueue(json(200, """
                {"output":{"results":[{"index":0,"relevance_score":0.3}]}}
                """));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isTrue();
        assertThat(response.getSources()).hasSize(1);
    }

    @Test
    void emptyAfterMinScoreSkipsTheModel() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("low", 0.2)));
        server.enqueue(json(200, """
                {"output":{"results":[{"index":0,"relevance_score":0.1}]}}
                """));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isTrue();
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getAnswer()).contains("没有找到相关信息");
        verify(aiClient, never()).chat(anyString());
    }

    @Test
    void fallsBackWhenCallTimesOut() {
        List<VectorStorePort.SearchResult> recalled = List.of(hit("near", 0.1), hit("far", 0.4));
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble())).thenReturn(recalled);
        server.enqueue(json(200, """
                {"output":{"results":[
                  {"index":1,"relevance_score":0.99},
                  {"index":0,"relevance_score":0.5}
                ]}}
                """).setHeadersDelay(3, TimeUnit.SECONDS));

        long started = System.nanoTime();
        RagQueryResponse response = service.queryWithContext(principal, "question");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(elapsedMs).isBetween(400L, 2500L);
        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(fallbackCount()).isEqualTo(1.0);
        assertThat(response.getAnswer()).isEqualTo("answer");
    }

    @Test
    void fallsBackWhenResponseBodyStalls() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));
        server.enqueue(json(200, """
                {"output":{"results":[
                  {"index":1,"relevance_score":0.99},
                  {"index":0,"relevance_score":0.5}
                ]}}
                """).setBodyDelay(5, TimeUnit.SECONDS));

        long started = System.nanoTime();
        RagQueryResponse response = service.queryWithContext(principal, "question");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(elapsedMs).isBetween(400L, 2500L);
        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(fallbackCount()).isEqualTo(1.0);
        assertThat(response.getAnswer()).isEqualTo("answer");
    }

    @Test
    void fallsBackOn4xx() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));
        server.enqueue(json(400, "{\"code\":\"InvalidParameter\",\"message\":\"bad request\",\"request_id\":\"req-400\"}"));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    void fallsBackWhenIndexIsDuplicated() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));
        server.enqueue(json(200, """
                {"output":{"results":[
                  {"index":0,"relevance_score":0.9},
                  {"index":0,"relevance_score":0.4}
                ]}}
                """));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    void fallsBackWhenIndexDoesNotFitInInt() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));
        server.enqueue(json(200, """
                {"output":{"results":[{"index":4294967296,"relevance_score":0.9}]}}
                """));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    void fallsBackWhenApiKeyIsBlank() {
        var embeddingModel = mock(org.springframework.ai.embedding.EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f, 0f, 0f});
        DashScopeReranker reranker = new DashScopeReranker(ragConfig, objectMapper, "  ");
        RagQueryService blankKey = new RagQueryService(
                vectorStore, embeddingModel, aiClient, ragConfig, new NoopReranker(), reranker, meterRegistry);
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));

        RagQueryResponse response = blankKey.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(server.getRequestCount()).isZero();
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    void fallsBackOn5xx() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));
        server.enqueue(json(503, "{\"code\":\"InternalError\",\"message\":\"unavailable\",\"request_id\":\"req-503\"}"));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    void fallsBackWhenResponseCannotBeParsed() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));
        server.enqueue(json(200, "not-json"));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    void fallsBackWhenIndexCannotBeMapped() {
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));
        server.enqueue(json(200, """
                {"output":{"results":[{"index":9,"relevance_score":0.8}]}}
                """));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    void disabledUsesVectorOrderAndDoesNotCallRerank() throws Exception {
        ragConfig.getRerank().setEnabled(false);
        ragConfig.getRerank().setMinScore(0.99);
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.2), hit("far", 0.25)));
        server.enqueue(json(200, """
                {"output":{"results":[
                  {"index":1,"relevance_score":0.99},
                  {"index":0,"relevance_score":0.5}
                ]}}
                """));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(response.getSources().get(0).getSimilarity()).isEqualTo(0.8);
        assertThat(server.getRequestCount()).isZero();
        assertThat(fallbackCount()).isZero();

        org.mockito.ArgumentCaptor<Integer> limit = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(vectorStore).searchSimilar(any(), any(float[].class), limit.capture(), anyDouble());
        assertThat(limit.getValue()).isEqualTo(2);
    }

    @Test
    void refusesRetiredGteModelAndFallsBack() {
        ragConfig.getRerank().setModel("gte-rerank-v2");
        when(vectorStore.searchSimilar(any(), any(float[].class), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("near", 0.1), hit("far", 0.4)));

        RagQueryResponse response = service.queryWithContext(principal, "question");

        assertThat(response.isReranked()).isFalse();
        assertThat(response.getSources()).extracting(RagQueryResponse.DocumentSource::getExcerpt)
                .containsExactly("near", "far");
        assertThat(server.getRequestCount()).isZero();
        assertThat(fallbackCount()).isEqualTo(1.0);
    }

    @Test
    void responseKeepsExistingFieldsAndAddsReranked() throws Exception {
        RagQueryResponse response = new RagQueryResponse("answer", List.of(), "qwen-plus", 12L, true);
        JsonNode node = objectMapper.valueToTree(response);

        assertThat(node.get("answer").asText()).isEqualTo("answer");
        assertThat(node.get("sources").isArray()).isTrue();
        assertThat(node.get("model").asText()).isEqualTo("qwen-plus");
        assertThat(node.get("latencyMs").asLong()).isEqualTo(12L);
        assertThat(node.get("reranked").asBoolean()).isTrue();

        JsonNode legacy = objectMapper.valueToTree(new RagQueryResponse("answer", List.of(), "qwen-plus", 12L));
        assertThat(legacy.get("reranked").asBoolean()).isFalse();
    }

    private double fallbackCount() {
        return meterRegistry.counter("rag.rerank.fallback").count();
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static VectorStorePort.SearchResult hit(String content, double distance) {
        return new VectorStorePort.SearchResult(
                UUID.randomUUID(),
                content,
                1,
                "text",
                distance,
                "{\"file_name\":\"" + content + ".txt\",\"source\":\"upload\"}");
    }
}
