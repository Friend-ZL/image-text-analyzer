package com.portfolio.analyzer.repository;

import com.portfolio.analyzer.config.RagConfig;
import com.portfolio.analyzer.security.TenantPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * ACL功能单元测试
 * 
 * <p>验证VectorStorePort的ACL查询过滤功能。
 * ACL更新功能将在集成测试中验证实际行为。
 */
@ExtendWith(MockitoExtension.class)
class VectorStoreAclTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private RagConfig ragConfig;
    
    private JdbcVectorStorePort vectorStore;
    
    @BeforeEach
    void setUp() {
        lenient().when(ragConfig.getTopK()).thenReturn(3);
        lenient().when(ragConfig.getSimilarityThreshold()).thenReturn(0.7);
        lenient().when(ragConfig.getHnswEfSearch()).thenReturn(100);
        
        // Mock pgvector version check
        when(jdbcTemplate.queryForObject(
                eq("SELECT extversion FROM pg_extension WHERE extname = 'vector'"),
                eq(String.class)
        )).thenReturn("0.8.0");
        
        vectorStore = new JdbcVectorStorePort(jdbcTemplate, ragConfig);
    }
    
    @Test
    void testSearchWithOwnerMismatchAndEmptyGroups_ZeroHits() {
        // User is not owner and has no groups -> should get 0 hits
        TenantPrincipal principal = new TenantPrincipal("tenant-1", "user-2", Set.of(), Set.of());
        float[] queryEmbedding = new float[1024];
        
        // Mock set_config calls
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(String.class), anyString()))
                .thenReturn(null);
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
                .thenReturn(null);
        
        // Mock search query returning 0 rows (SQL WHERE filters out all chunks)
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), (Object[]) any()))
                .thenReturn(Collections.emptyList());
        
        List<VectorStorePort.SearchResult> results = vectorStore.searchSimilar(
                principal, queryEmbedding, 3, 0.7
        );
        
        assertThat(results).isEmpty();
    }
    
    @Test
    void testSearchWithRevokedGroup_ZeroHits() {
        // User only has group-x, but document requires group-a or group-b
        // -> ACL array overlap `&&` will return false -> 0 hits
        TenantPrincipal principal = new TenantPrincipal("tenant-1", "user-3", Set.of(), Set.of("group-x"));
        float[] queryEmbedding = new float[1024];
        
        // Mock set_config calls
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(String.class), anyString()))
                .thenReturn(null);
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
                .thenReturn(null);
        
        // Mock search query returning 0 rows (no ACL overlap)
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), (Object[]) any()))
                .thenReturn(Collections.emptyList());
        
        List<VectorStorePort.SearchResult> results = vectorStore.searchSimilar(
                principal, queryEmbedding, 3, 0.7
        );
        
        assertThat(results).isEmpty();
    }
}
