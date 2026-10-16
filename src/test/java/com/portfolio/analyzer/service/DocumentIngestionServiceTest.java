package com.portfolio.analyzer.service;

import com.portfolio.analyzer.config.RagConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private RagConfig ragConfig;

    private DocumentIngestionService documentIngestionService;

    @BeforeEach
    void setUp() {
        lenient().when(ragConfig.getChunkSize()).thenReturn(500);
        lenient().when(ragConfig.getChunkOverlap()).thenReturn(50);
        documentIngestionService = new DocumentIngestionService(vectorStore, ragConfig);
    }

    @Test
    void testIngestTextDocument_Success() throws IOException {
        String testContent = "This is a test document. It contains multiple sentences. " +
                "We will test if it can be properly chunked and stored.";
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                testContent.getBytes()
        );

        int chunksProcessed = documentIngestionService.ingestDocument(file, "test-source");

        assertTrue(chunksProcessed > 0, "Should process at least one chunk");
        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    void testIngestTextDocument_EmptyFile() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () -> {
            documentIngestionService.ingestDocument(file, "test-source");
        });
    }

    @Test
    void testIngestTextDocument_NullFile() {
        assertThrows(IllegalArgumentException.class, () -> {
            documentIngestionService.ingestDocument(null, "test-source");
        });
    }

    @Test
    void testIngestTextDocument_WithMetadata() throws IOException {
        String testContent = "Test content with metadata.";
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                testContent.getBytes()
        );

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);

        documentIngestionService.ingestDocument(file, "test-source");

        verify(vectorStore).add(captor.capture());
        List<Document> documents = captor.getValue();
        assertFalse(documents.isEmpty());
        
        Document firstDoc = documents.get(0);
        Map<String, Object> metadata = firstDoc.getMetadata();
        assertEquals("test-source", metadata.get("source"));
    }

    @Test
    void testIngestTextDocument_LargeContent() throws IOException {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is sentence number ").append(i).append(". ");
        }
        
        MultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                largeContent.toString().getBytes()
        );

        int chunksProcessed = documentIngestionService.ingestDocument(file, "large-doc");

        assertTrue(chunksProcessed > 1, "Large content should be split into multiple chunks");
        verify(vectorStore, times(1)).add(anyList());
    }
}
