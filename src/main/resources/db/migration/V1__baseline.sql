-- V1__baseline.sql
-- 基线迁移：仅做基础设施准备
-- 
-- 说明：
-- 1. RAG 相关的 chunk 表由 AI-RAG 工程师在 PR #2 中以 V2 及之后版本提交
-- 2. 此迁移只做必要的通用基础设施准备

-- 启用 pgvector 扩展（如果不存在）
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建用于测试的基础 schema（如果需要）
-- 生产环境的 schema 和角色准备由 DevOps 负责

-- 基线完成标记
COMMENT ON EXTENSION vector IS 'Vector similarity search extension for RAG';
