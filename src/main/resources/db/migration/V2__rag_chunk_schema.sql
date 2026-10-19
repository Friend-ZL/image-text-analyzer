-- V2: RAG chunk schema for Stage 2
--
-- document: metadata table (tenant_id, owner, ACL, file info)
-- chunk: vector embeddings with explicit tenant/ACL columns (NOT JSONB)
--
-- Security: tenant_id/owner_user_id/acl_groups are real columns for WHERE filtering.
-- Backend will add RLS policies in V3 using SET LOCAL app.tenant_id per transaction.
-- Explicit WHERE conditions provide defense in depth.

-- Document metadata table
CREATE TABLE document (
    document_id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(128),
    acl_groups TEXT[] NOT NULL DEFAULT '{}',
    file_name VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    source VARCHAR(255),
    page_count INT,
    total_chunks INT,
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dim INT NOT NULL CHECK (embedding_dim = 1024),
    ingested_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (document_id),
    CONSTRAINT uq_document_tenant_id UNIQUE (tenant_id, document_id)
);

-- Index for tenant+owner queries (covers tenant-only prefix)
CREATE INDEX idx_document_tenant_owner ON document(tenant_id, owner_user_id);

-- Chunk table with explicit security columns (tenant_id, owner_user_id, acl_groups)
-- Tenant/ACL filtering is a security boundary and uses real columns, not JSONB
CREATE TABLE chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    doc_id UUID NOT NULL,
    page_no INT,
    chunk_index INT NOT NULL,
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('text', 'vlm_ocr', 'image_desc')),
    owner_user_id VARCHAR(128),
    acl_groups TEXT[] NOT NULL DEFAULT '{}',
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dim INT NOT NULL CHECK (embedding_dim = 1024),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    -- Idempotent re-ingestion: UNIQUE (tenant_id, doc_id, chunk_index)
    CONSTRAINT uq_chunk_tenant_doc_index UNIQUE (tenant_id, doc_id, chunk_index),
    -- Tenant-consistent FK: ensures chunk.tenant_id = document.tenant_id
    CONSTRAINT fk_chunk_document FOREIGN KEY (tenant_id, doc_id) 
        REFERENCES document(tenant_id, document_id) ON DELETE CASCADE
);

-- ACL filtering index (GIN for array overlap operator &&)
CREATE INDEX idx_chunk_acl_groups ON chunk USING GIN(acl_groups);

-- Vector similarity search index
CREATE INDEX idx_chunk_embedding ON chunk USING hnsw (embedding vector_cosine_ops);

-- Comments for documentation
COMMENT ON TABLE document IS 'Document metadata with tenant isolation';
COMMENT ON TABLE chunk IS 'Document chunks with explicit tenant/ACL columns for security boundary enforcement';
COMMENT ON COLUMN chunk.tenant_id IS 'Security boundary: must match TenantContext.getTenantId()';
COMMENT ON COLUMN chunk.owner_user_id IS 'Security boundary: document owner (can access regardless of groups)';
COMMENT ON COLUMN chunk.acl_groups IS 'Security boundary: text[] of authorized groups (PostgreSQL array operator &&)';
COMMENT ON COLUMN chunk.metadata IS 'Non-security metadata only (file_name, content_type, etc.)';
COMMENT ON COLUMN chunk.embedding IS '1024-dimensional vector from text-embedding-v4';
COMMENT ON COLUMN chunk.source_type IS 'text = plain text chunk, vlm_ocr = OCR from rendered PDF page, image_desc = VLM description';
COMMENT ON CONSTRAINT uq_document_tenant_id ON document IS 'Tenant-consistent FK support: document PK includes tenant_id';
COMMENT ON CONSTRAINT fk_chunk_document ON chunk IS 'Tenant-consistent FK: enforces chunk.tenant_id = document.tenant_id';

-- Note: Backend will add Row-Level Security (RLS) policies in V3
-- RLS will use SET LOCAL app.tenant_id per transaction (via set_config)
-- The explicit WHERE tenant_id/ACL conditions in SQL provide defense in depth
