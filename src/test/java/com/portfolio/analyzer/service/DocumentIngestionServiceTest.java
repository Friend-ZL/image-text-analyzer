package com.portfolio.analyzer.service;

import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.DocumentChunk;
import com.portfolio.analyzer.dto.DocumentPage;
import com.portfolio.analyzer.dto.SourceType;
import com.portfolio.analyzer.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private RagConfig ragConfig;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private DocumentExtractionService extractionService;

    @Mock
    private DocumentChunkingService chunkingService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DocumentIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.getTenantId()).thenReturn("test-tenant");
        lenient().when(tenantContext.getUserId()).thenReturn("test-user");
        lenient().when(tenantContext.getGroups()).thenReturn(Arrays.asList("group1"));

        ingestionService = new DocumentIngestionService(
                vectorStore, ragConfig, tenantContext, extractionService, chunkingService, jdbcTemplate
        );
    }

    @Test
    void testIngestDocument_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Test content".getBytes()
        );

        DocumentPage page = new DocumentPage(1, "Test content", SourceType.TEXT);
        DocumentChunk chunk = new DocumentChunk(1, 0, "Test content", SourceType.TEXT);

        when(extractionService.extractPages(any())).thenReturn(Arrays.asList(page));
        when(chunkingService.chunkPages(any())).thenReturn(Arrays.asList(chunk));
        doNothing().when(vectorStore).add(anyList());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = ingestionService.ingestDocument(file, "test-source");

        assertNotNull(result);
        assertTrue(result.containsKey("documentId"));
        assertEquals(1, result.get("pagesProcessed"));
        assertEquals(1, result.get("chunksProcessed"));
        assertTrue((Long) result.get("processingTimeMs") >= 0);

        verify(extractionService).extractPages(any());
        verify(chunkingService).chunkPages(any());
        verify(vectorStore).add(anyList());
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void testIngestDocument_NullFile() {
        assertThrows(IllegalArgumentException.class, () -> {
            ingestionService.ingestDocument(null, "source");
        });
    }

    @Test
    void testIngestDocument_EmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () -> {
            ingestionService.ingestDocument(emptyFile, "source");
        });
    }

    @Test
    void testIngestDocument_MultiplePages() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "multipage.txt",
                "text/plain",
                "Long content".getBytes()
        );

        List<DocumentPage> pages = Arrays.asList(
                new DocumentPage(1, "Page 1", SourceType.TEXT),
                new DocumentPage(2, "Page 2", SourceType.TEXT)
        );

        List<DocumentChunk> chunks = Arrays.asList(
                new DocumentChunk(1, 0, "Page 1", SourceType.TEXT),
                new DocumentChunk(2, 0, "Page 2", SourceType.TEXT)
        );

        when(extractionService.extractPages(any())).thenReturn(pages);
        when(chunkingService.chunkPages(any())).thenReturn(chunks);
        doNothing().when(vectorStore).add(anyList());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = ingestionService.ingestDocument(file, "test-source");

        assertNotNull(result);
        assertEquals(2, result.get("pagesProcessed"));
        assertEquals(2, result.get("chunksProcessed"));
    }
}
