package com.portfolio.analyzer.integration;

import com.portfolio.analyzer.ai.MultimodalAIClient;
import com.portfolio.analyzer.config.RagConfig;
import org.flywaydb.core.api.callback.Callback;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deterministic model stubs shared by every pgvector IT. External models are
 * never called. The database is the real Testcontainers instance.
 */
@TestConfiguration
public class ItStubsConfig {

    @Bean
    static BeanPostProcessor hnswIterativeScanProbe() {
        return new HnswIterativeScanProbe();
    }

    @Bean
    Callback appRwGrantsAfterFlyway() {
        return AppRwRoleBootstrap.callback();
    }

    /**
     * 1024-dimensional vectors whose pairwise cosine similarity stays above
     * {@code app.rag.similarity-threshold}. Tenant and ACL checks are SQL, not
     * a property of the vectors.
     */
    @Bean
    @Primary
    public EmbeddingModel stubEmbeddingModel(RagConfig ragConfig) {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        assertThat(ragConfig.getEmbeddingDimensions()).isEqualTo(1024);
        assertThat(ragConfig.getEmbeddingModel()).isEqualTo("text-embedding-v4");
        when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            float[] embedding = new float[1024];
            Arrays.fill(embedding, 1.0f);
            int hash = text == null ? 0 : text.hashCode();
            embedding[Math.floorMod(hash, 1024)] += 0.05f;
            double norm = 0.0;
            for (float value : embedding) {
                norm += (double) value * value;
            }
            norm = Math.sqrt(norm);
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= (float) norm;
            }
            return embedding;
        });
        when(embeddingModel.dimensions()).thenReturn(1024);
        return embeddingModel;
    }

    @Bean
    @Primary
    public MultimodalAIClient stubMultimodalAIClient() {
        MultimodalAIClient client = mock(MultimodalAIClient.class);
        when(client.chat(anyString())).thenAnswer(invocation -> {
            CapturedChatPrompt.record(invocation.getArgument(0));
            return "stub answer 【数据块1】";
        });
        when(client.analyzeImages(anyList(), anyString()))
                .thenReturn("stub ocr text long enough to stand in for a scanned page");
        when(client.getTextModelName()).thenReturn("qwen-plus");
        when(client.getModelName()).thenReturn("qwen-vl-max");
        return client;
    }
}
