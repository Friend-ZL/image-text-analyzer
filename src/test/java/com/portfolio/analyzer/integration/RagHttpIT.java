package com.portfolio.analyzer.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.analyzer.security.TestJwtSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-port HTTP against the JWT filter chain and pgvector. Tokens are signed
 * by {@link TestJwtSupport} for {@code TestJwtDecoderConfiguration}.
 */
class RagHttpIT extends AbstractPgVectorIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestPdfThenQueryReturnsPageNumberAndFileName() throws Exception {
        byte[] pdf = new ClassPathResource("test-data/test.pdf").getContentAsByteArray();
        int pdfPages;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            pdfPages = document.getNumberOfPages();
        }
        assertThat(pdfPages).isPositive();

        String token = TestJwtSupport.token("pdf-user", "tenant-pdf", List.of("readers"));
        ResponseEntity<String> ingest = postFile(token, pdf, "test.pdf", MediaType.APPLICATION_PDF);
        assertThat(ingest.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode ingestBody = objectMapper.readTree(ingest.getBody());
        assertThat(ingestBody.get("code").asText()).isEqualTo("00000");
        String documentId = ingestBody.get("data").get("documentId").asText();

        ResponseEntity<String> query = postQuery(token, "document");
        assertThat(query.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sources = objectMapper.readTree(query.getBody()).path("data").path("sources");
        assertThat(sources.isArray()).isTrue();
        assertThat(sources.size()).isPositive();

        List<Map<String, Object>> chunks = jdbcTemplate.queryForList(
                "SELECT page_no, content, metadata->>'file_name' AS file_name " +
                        "FROM chunk WHERE tenant_id = ? AND doc_id = ?",
                "tenant-pdf",
                UUID.fromString(documentId)
        );
        assertThat(chunks).isNotEmpty();

        for (JsonNode source : sources) {
            assertThat(source.get("fileName").asText()).isEqualTo("test.pdf");
            int page = source.get("pageNumber").asInt();
            assertThat(page).isBetween(1, pdfPages);
            String excerpt = source.get("excerpt").asText();
            String prefix = excerpt.endsWith("...") ? excerpt.substring(0, excerpt.length() - 3) : excerpt;
            assertThat(chunks).anySatisfy(row -> {
                assertThat(((Number) row.get("page_no")).intValue()).isEqualTo(page);
                assertThat(String.valueOf(row.get("content"))).startsWith(prefix);
                assertThat(row.get("file_name")).isEqualTo("test.pdf");
            });
        }
    }

    @Test
    void queryWithoutTokenReturns401() throws Exception {
        ResponseEntity<String> response = postQuery(null, "hello");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(objectMapper.readTree(response.getBody()).get("code").asText()).isEqualTo("E40101");
    }

    @Test
    void tokenWithoutTenantReturns401E40102() throws Exception {
        String token = TestJwtSupport.token("user-no-tenant", null, List.of());
        ResponseEntity<String> response = postQuery(token, "hello");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("E40102");
    }

    @Test
    void otherTenantTokenSeesNoSources() throws Exception {
        byte[] pdf = new ClassPathResource("test-data/test.pdf").getContentAsByteArray();
        String owner = TestJwtSupport.token("pdf-owner", "tenant-http-a", List.of("readers"));
        ResponseEntity<String> ingest = postFile(owner, pdf, "test.pdf", MediaType.APPLICATION_PDF);
        assertThat(ingest.getStatusCode()).isEqualTo(HttpStatus.OK);

        String other = TestJwtSupport.token("pdf-other", "tenant-http-b", List.of("readers"));
        ResponseEntity<String> query = postQuery(other, "document");
        assertThat(query.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(query.getBody());
        assertThat(body.get("code").asText()).isEqualTo("00000");
        assertThat(body.path("data").path("sources")).isEmpty();
        assertThat(body.path("data").path("answer").asText()).contains("没有找到相关信息");
    }

    private ResponseEntity<String> postQuery(String token, String question) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        // Spring's HttpURLConnection factory always streams the body. A 401 with
        // WWW-Authenticate then throws "cannot retry due to server authentication,
        // in streaming mode" before the status can be read. The JDK client does not.
        RestTemplate http = new RestTemplate(new JdkClientHttpRequestFactory());
        http.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        String json = objectMapper.writeValueAsString(Map.of("question", question));
        return http.postForEntity(restTemplate.getRootUri() + "/api/v1/rag/query",
                new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> postFile(String token, byte[] bytes, String filename, MediaType mediaType) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mediaType);
        fileHeaders.setContentDispositionFormData("file", filename);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        body.add("source", "it-http");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.postForEntity(
                "/api/v1/rag/ingest",
                new HttpEntity<>(body, headers),
                String.class
        );
    }
}
