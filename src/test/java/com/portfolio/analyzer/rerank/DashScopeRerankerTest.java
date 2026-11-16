package com.portfolio.analyzer.rerank;

import com.portfolio.analyzer.config.RagConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeRerankerTest {

    @Test
    void endpointUsesWorkspaceRegionAndDomain() {
        RagConfig.Rerank rerank = new RagConfig.Rerank();
        rerank.setWorkspaceId("ws-1");
        rerank.setRegion("ap-southeast-1");
        rerank.setDomain("maas.aliyuncs.com");

        URI uri = DashScopeReranker.resolveEndpoint(rerank);

        assertThat(uri).hasToString(
                "https://ws-1.ap-southeast-1.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");
    }

    @Test
    void beijingDefaultOmitsEmptyWorkspace() {
        URI uri = DashScopeReranker.resolveEndpoint(new RagConfig.Rerank());

        assertThat(uri).hasToString(
                "https://cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");
    }

    @Test
    void baseUrlOverridesDomainAndRegion() {
        RagConfig.Rerank rerank = new RagConfig.Rerank();
        rerank.setRegion("us-east-1");
        rerank.setDomain("maas.aliyuncs.com");
        rerank.setWorkspaceId("ws-1");
        rerank.setBaseUrl("http://127.0.0.1:9/");

        URI uri = DashScopeReranker.resolveEndpoint(rerank);

        assertThat(uri).hasToString(
                "http://127.0.0.1:9/api/v1/services/rerank/text-rerank/text-rerank");
    }

    @Test
    void fullPathBaseUrlIsNotDoubled() {
        RagConfig.Rerank rerank = new RagConfig.Rerank();
        rerank.setBaseUrl("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");

        URI uri = DashScopeReranker.resolveEndpoint(rerank);

        assertThat(uri).hasToString(
                "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");
    }
}
