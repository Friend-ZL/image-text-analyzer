-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Document metadata table
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

-- Document chunk table (vector store)
CREATE TABLE document_chunk (
    chunk_id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(document_id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    page_number INT,
    chunk_index INT,
    source_type VARCHAR(20),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dim INT NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chunk_tenant ON document_chunk(tenant_id);
CREATE INDEX idx_chunk_document ON document_chunk(document_id);
CREATE INDEX idx_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);

-- Comments for documentation
COMMENT ON TABLE document IS 'Document metadata with tenant isolation';
COMMENT ON TABLE document_chunk IS 'Document chunks with embeddings for RAG retrieval';
COMMENT ON COLUMN document.acl_groups IS 'JSON array of group/role names that can access this document';
COMMENT ON COLUMN document_chunk.source_type IS 'Source of content: text, vlm_ocr, or image_desc';
COMMENT ON COLUMN document_chunk.embedding IS '1024-dimensional vector from text-embedding-v4';
