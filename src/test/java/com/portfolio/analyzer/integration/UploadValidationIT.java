package com.portfolio.analyzer.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.security.TestJwtSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upload checks that already exist on the ingest path. Extension-versus-magic
 * mismatch for two supported types is not implemented; see the PR description.
 */
class UploadValidationIT extends AbstractPgVectorIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagConfig ragConfig;

    @Test
    void unsupportedMagicBytesAreRejectedDespitePdfExtension() throws Exception {
        byte[] zip = new byte[] {
                0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00
        };
        String token = TestJwtSupport.token("upload-user", "tenant-upload", List.of());
        ResponseEntity<String> response = postFile(token, zip, "report.pdf", MediaType.APPLICATION_PDF);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("E41501");
    }

    @Test
    void pdfExceedingMaxPageLimitIsRejected() throws Exception {
        int limit = ragConfig.getMaxPdfPages();
        assertThat(limit).isPositive();
        byte[] pdf = blankPdf(limit + 1);
        String token = TestJwtSupport.token("upload-user", "tenant-upload-pages", List.of());
        ResponseEntity<String> response = postFile(token, pdf, "too-many.pdf", MediaType.APPLICATION_PDF);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("E41301");
    }

    private static byte[] blankPdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage(PDRectangle.LETTER));
            }
            document.save(out);
            return out.toByteArray();
        }
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
        body.add("source", "it-upload");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return restTemplate.postForEntity(
                "/api/v1/rag/ingest",
                new HttpEntity<>(body, headers),
                String.class
        );
    }
}
