-- V2: RAG chunk schema for Stage 2
--
-- document: metadata table (tenant_id, owner, ACL, file info)
-- chunk: vector embeddings with explicit tenant/ACL columns (NOT JSONB)
--
-- Security: tenant_id/owner_user_id/acl_groups are real columns for WHERE filtering.
-- Backend will add RLS policies using SET LOCAL app.tenant_id per transaction.
-- Explicit WHERE conditions provide defense in depth.

-- Document metadata table
CREATE TABLE document (
    document_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(128),
    acl_groups TEXT[],
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

-- Chunk table with explicit security columns (tenant_id, owner_user_id, acl_groups)
-- Tenant/ACL filtering is a security boundary and uses real columns, not JSONB
CREATE TABLE chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    doc_id UUID NOT NULL REFERENCES document(document_id) ON DELETE CASCADE,
    page_no INT,
    chunk_index INT NOT NULL,
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('text', 'vlm_ocr', 'image_desc')),
    owner_user_id VARCHAR(128),
    acl_groups TEXT[],
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dim INT NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Idempotent re-ingestion: UNIQUE (tenant_id, doc_id, chunk_index)
    CONSTRAINT uq_chunk_tenant_doc_index UNIQUE (tenant_id, doc_id, chunk_index)
);

-- Security-critical indexes for tenant/ACL filtering (before vector search)
CREATE INDEX idx_chunk_tenant ON chunk(tenant_id);
CREATE INDEX idx_chunk_acl_groups ON chunk USING GIN(acl_groups);

-- Vector similarity search index
CREATE INDEX idx_chunk_embedding ON chunk USING hnsw (embedding vector_cosine_ops);

-- Foreign key index
CREATE INDEX idx_chunk_doc_id ON chunk(doc_id);

-- Comments for documentation
COMMENT ON TABLE document IS 'Document metadata with tenant isolation';
COMMENT ON TABLE chunk IS 'Document chunks with explicit tenant/ACL columns for security boundary enforcement';
COMMENT ON COLUMN chunk.tenant_id IS 'Security boundary: must match TenantContext.getTenantId()';
COMMENT ON COLUMN chunk.owner_user_id IS 'Security boundary: document owner (can access regardless of groups)';
COMMENT ON COLUMN chunk.acl_groups IS 'Security boundary: text[] of authorized groups (PostgreSQL array operator &&)';
COMMENT ON COLUMN chunk.metadata IS 'Non-security metadata only (file_name, content_type, etc.)';
COMMENT ON COLUMN chunk.embedding IS '1024-dimensional vector from text-embedding-v4';
COMMENT ON COLUMN chunk.source_type IS 'text = plain text chunk, vlm_ocr = OCR from rendered PDF page, image_desc = VLM description';

-- Note: Backend will add Row-Level Security (RLS) policies on chunk table in production
-- RLS will use SET LOCAL app.tenant_id per transaction
-- The explicit WHERE tenant_id/ACL conditions in SQL provide defense in depth
