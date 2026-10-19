package com.portfolio.analyzer.service;

import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.dto.DocumentChunk;
import com.portfolio.analyzer.dto.DocumentPage;
import com.portfolio.analyzer.dto.SourceType;
import com.portfolio.analyzer.repository.VectorStorePort;
import com.portfolio.analyzer.security.TenantPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {
    
    @Mock
    private VectorStorePort vectorStore;
    
    @Mock
    private EmbeddingModel embeddingModel;
    
    @Mock
    private RagConfig ragConfig;
    
    @Mock
    private DocumentExtractionService extractionService;
    
    @Mock
    private DocumentChunkingService chunkingService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    private DocumentIngestionService ingestionService;
    private TenantPrincipal testPrincipal;
    
    @BeforeEach
    void setUp() {
        lenient().when(ragConfig.getEmbeddingModel()).thenReturn("text-embedding-v4");
        lenient().when(ragConfig.getEmbeddingDimensions()).thenReturn(1024);
        
        ingestionService = new DocumentIngestionService(
                vectorStore, embeddingModel, ragConfig,
                extractionService, chunkingService, jdbcTemplate
        );
        
        testPrincipal = new TenantPrincipal("test-tenant", "test-user", Set.of(), Set.of("group1"));
    }
    
    @Test
    void testIngestDocument_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes()
        );
        
        DocumentPage page = new DocumentPage(1, "test content", SourceType.TEXT);
        DocumentChunk chunk = new DocumentChunk(1, 0, "test content", SourceType.TEXT);
        
        float[] embedding = new float[1024];
        Arrays.fill(embedding, 0.5f);
        
        when(extractionService.extractPages(any())).thenReturn(List.of(page));
        when(chunkingService.chunkPages(anyList())).thenReturn(List.of(chunk));
        when(embeddingModel.embed(anyString())).thenReturn(embedding);
        
        // Lenient stub for jdbcTemplate.update (varargs)
        lenient().when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(1);
        when(vectorStore.upsertChunks(any(TenantPrincipal.class), anyList())).thenReturn(1);
        
        Map<String, Object> result = ingestionService.ingestDocument(testPrincipal, file, "test-source");
        
        assertThat(result).isNotNull();
        assertThat(result.get("documentId")).isNotNull();
        assertThat(result.get("pagesProcessed")).isEqualTo(1);
        assertThat(result.get("chunksProcessed")).isEqualTo(1);
        
        verify(vectorStore, times(1)).upsertChunks(any(TenantPrincipal.class), anyList());
    }
    
    @Test
    void testIngestDocument_NullFile() {
        assertThatThrownBy(() -> ingestionService.ingestDocument(testPrincipal, null, "source"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件不能为空");
    }
    
    @Test
    void testIngestDocument_EmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]
        );
        
        assertThatThrownBy(() -> ingestionService.ingestDocument(testPrincipal, emptyFile, "source"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件不能为空");
    }
    
    @Test
    void testIngestDocument_MultiplePages() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "multipage.txt", "text/plain", "test content".getBytes()
        );
        
        List<DocumentPage> pages = List.of(
                new DocumentPage(1, "page 1", SourceType.TEXT),
                new DocumentPage(2, "page 2", SourceType.TEXT)
        );
        List<DocumentChunk> chunks = List.of(
                new DocumentChunk(1, 0, "page 1", SourceType.TEXT),
                new DocumentChunk(2, 0, "page 2", SourceType.TEXT)
        );
        
        float[] embedding = new float[1024];
        
        when(extractionService.extractPages(any())).thenReturn(pages);
        when(chunkingService.chunkPages(anyList())).thenReturn(chunks);
        when(embeddingModel.embed(anyString())).thenReturn(embedding);
        lenient().when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(1);
        when(vectorStore.upsertChunks(any(TenantPrincipal.class), anyList())).thenReturn(2);
        
        Map<String, Object> result = ingestionService.ingestDocument(testPrincipal, file, "test-source");
        
        assertThat(result.get("pagesProcessed")).isEqualTo(2);
        assertThat(result.get("chunksProcessed")).isEqualTo(2);
    }
}
