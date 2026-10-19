-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Document metadata table (for management)
CREATE TABLE document (
    document_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(128),
    acl_groups JSONB,
    file_name VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    source VARCHAR(255),
    page_count INT,
    total_chunks INT,
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dim INT NOT NULL,
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_tenant ON document(tenant_id);
CREATE INDEX idx_document_tenant_owner ON document(tenant_id, owner_user_id);

-- Vector store table (compatible with Spring AI PgVectorStore)
-- Additional metadata stored in jsonb: tenant_id, document_id, page_number, etc.
CREATE TABLE vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1024) NOT NULL
);

-- Indexes for tenant filtering and vector search
CREATE INDEX idx_vector_tenant ON vector_store ((metadata->>'tenant_id'));
CREATE INDEX idx_vector_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Comments for documentation
COMMENT ON TABLE document IS 'Document metadata with tenant isolation (for management)';
COMMENT ON TABLE vector_store IS 'Vector store compatible with Spring AI PgVectorStore, with tenant filtering support';
COMMENT ON COLUMN vector_store.metadata IS 'JSONB containing: tenant_id, document_id, owner_user_id, acl_groups, page_number, chunk_index, source_type, embedding_model, embedding_dim, source, file_name, content_type';
COMMENT ON COLUMN vector_store.embedding IS '1024-dimensional vector from text-embedding-v4';
