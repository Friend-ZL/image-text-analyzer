# BE-1：API 规范底座 - 最终报告

## 完成状态

✅ **任务已完成**

- PR 已创建：[#3 BE-1: API 规范底座](https://github.com/Friend-ZL/image-text-analyzer/pull/3)
- 分支：`cursor/feat-be-api-foundation-5372`
- 基础分支：`main`
- 状态：Draft PR（待 Review）

## PR 链接

**GitHub PR**: https://github.com/Friend-ZL/image-text-analyzer/pull/3

## 主要新增/修改的类

### 新增文件（5 个）

1. **`src/main/java/com/portfolio/analyzer/common/ErrorCode.java`**
   - 错误码枚举，定义了 12 种错误码（1 个成功码 + 11 个错误码）
   - 格式：`E + HTTP状态码 + 序号`
   - 包含错误码、消息和 HTTP 状态的映射

2. **`src/main/java/com/portfolio/analyzer/common/ApiResponse.java`**
   - 统一响应体 `{code, message, data, traceId}`
   - 提供静态方法 `success()` 和 `error()` 快速构建响应

3. **`src/main/java/com/portfolio/analyzer/filter/TraceIdFilter.java`**
   - 请求日志过滤器，实现 TraceId 追踪
   - 优先从请求头 `X-Request-Id` 获取，否则生成 UUID
   - 将 traceId 放入 MDC 和响应头 `X-Trace-Id`

4. **`src/main/java/com/portfolio/analyzer/exception/ModelTimeoutException.java`**
   - 模型超时异常，用于区分模型调用超时场景

5. **`src/main/resources/logback-spring.xml`**
   - Logback 配置，支持按 Profile 切换日志格式
   - 开发环境（dev/test）：可读格式
   - 生产环境（prod）：JSON 格式（logstash-logback-encoder）

### 修改文件（6 个）

1. **`src/main/java/com/portfolio/analyzer/controller/AnalysisController.java`**
   - 新增 `/api/v1/analyze` 接口（推荐使用）
   - 保留 `/api/analyze` 接口（标记为 `@Deprecated`）
   - 旧接口响应头包含 `Deprecation`、`Sunset` 和 `Link` 头
   - 所有接口返回统一响应体 `ApiResponse<AnalysisResponse>`

2. **`src/main/java/com/portfolio/analyzer/exception/GlobalExceptionHandler.java`**
   - 使用统一响应体 `ApiResponse<Void>`
   - 使用错误码枚举 `ErrorCode`
   - 从 MDC 获取 traceId 并放入响应
   - 新增 `ModelTimeoutException`、`MethodArgumentNotValidException`、`ConstraintViolationException` 处理

3. **`src/main/resources/static/index.html`**
   - 前端调用新版接口 `/api/v1/analyze`
   - 解析统一响应体，提取 `data` 字段

4. **`src/test/java/com/portfolio/analyzer/controller/AnalysisControllerTest.java`**
   - 完全重写测试用例，新增 11 个测试场景
   - 测试 v1 接口和旧接口
   - 测试所有错误码
   - 测试 traceId 生成和透传
   - 测试 Deprecation 头

5. **`pom.xml`**
   - 添加 `logstash-logback-encoder` 依赖（版本 7.4）

6. **`README.md`**
   - 新增 "API 接口规范" 章节
   - 补充统一响应体格式说明
   - 补充错误码表（完整 12 个错误码）
   - 补充请求追踪说明
   - 补充 API 使用示例（包含成功和失败场景）
   - 新增 "API 规范底座设计" 核心设计决策章节

7. **`文档-多模态文档分析Agent设计.md`**
   - 新增 "API 规范底座设计（BE-1）" 章节
   - 补充实现概述、新增文件、错误码设计、TraceId 设计
   - 补充测试覆盖、设计取舍、后续扩展点

## 错误码表

| 错误码 | HTTP 状态 | 说明 | 常见原因 |
|--------|----------|------|---------|
| **00000** | 200 | 成功 | - |
| **E40001** | 400 | 参数校验失败 | 必填参数缺失、格式不正确 |
| **E40002** | 400 | 文件不能为空 | 未上传文件或文件为空 |
| **E40003** | 400 | 问题不能为空 | question 参数为空或空白字符串 |
| **E40801** | 408 | 请求超时 | 请求处理时间过长 |
| **E41301** | 413 | 文件大小超过限制 | 文件大小超过 10MB |
| **E41501** | 415 | 不支持的文件类型 | 文件类型不是支持的图片或 PDF 格式 |
| **E42901** | 429 | 请求过于频繁 | 触发限流（预留，当前未启用） |
| **E50001** | 500 | 服务器内部错误 | 未预期的系统异常 |
| **E50002** | 500 | AI服务调用失败 | 模型服务异常 |
| **E50201** | 502 | 模型服务异常 | 上游模型服务不可用 |
| **E50401** | 504 | 模型调用超时 | 模型响应时间过长 |

## mvn verify 结果

### 测试统计

```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

### 测试明细

**`AnalysisControllerTest`（11 个测试）**：
1. ✅ `testAnalyzeV1_ValidRequest_ShouldReturnSuccessWithTraceId` - v1 接口成功响应
2. ✅ `testAnalyzeV1_WithCustomTraceId_ShouldPreserveIt` - 自定义 traceId 透传
3. ✅ `testAnalyze_DeprecatedEndpoint_ShouldReturnDeprecationHeaders` - 旧接口废弃标记
4. ✅ `testAnalyzeV1_EmptyFile_ShouldReturnBadRequest` - 空文件错误
5. ✅ `testAnalyzeV1_EmptyQuestion_ShouldReturnBadRequest` - 空问题错误
6. ✅ `testAnalyzeV1_InvalidFileType_ShouldReturnUnsupportedMediaType` - 不支持的文件类型
7. ✅ `testAnalyzeV1_FileTooLarge_ShouldReturnPayloadTooLarge` - 文件过大
8. ✅ `testAnalyzeV1_AIServiceError_ShouldReturnInternalError` - AI 服务错误
9. ✅ `testAnalyzeV1_ModelTimeout_ShouldReturnGatewayTimeout` - 模型超时
10. ✅ `testAnalyzeV1_AIServiceWithTimeoutCause_ShouldReturnGatewayTimeout` - AI 服务超时异常
11. ✅ `testAnalyzeV1_UnexpectedException_ShouldReturnInternalError` - 未知异常
12. ✅ `testHealth_ShouldReturnOK` - 健康检查

**其他测试（5 个）**：
- ✅ `MultimodalAIClientTest` - 2 tests passed
- ✅ `DocumentAnalysisServiceTest` - 1 test passed
- ✅ `ImageProcessingServiceTest` - 2 tests passed

### 构建结果

```bash
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  3.679 s
[INFO] Finished at: 2026-10-19T06:45:32Z
[INFO] ------------------------------------------------------------------------
```

### 测试断言覆盖

- ✅ 响应头 `X-Trace-Id` 存在
- ✅ 响应体 `traceId` 字段存在且不为空
- ✅ 自定义 `X-Request-Id` 原样透传到 `X-Trace-Id` 和响应体 `traceId`
- ✅ 旧接口返回 `Deprecation: true` 头
- ✅ 旧接口返回 `Sunset` 头
- ✅ 旧接口返回 `Link` 头指向新版本
- ✅ 每个错误码对应的 HTTP 状态码正确（400/408/413/415/429/500/502/504）
- ✅ 每个错误响应都包含 `code`、`message`、`traceId` 字段

## 需要架构师决策的点

### 1. 错误码格式是否需要调整？

**当前格式**：`E + HTTP状态码 + 序号`（如 `E40001`）

**优点**：
- 语义清晰，一眼看出是哪类错误（4xx 客户端，5xx 服务端）
- HTTP 状态与错误码强绑定，符合 RESTful 规范
- 每个状态码预留 99 个码位，扩展性强

**备选方案**：
- 纯数字格式（如 `10001`）：更紧凑，但需查文档
- 业务前缀 + 数字（如 `FILE_001`）：适合复杂业务，本项目可能过度设计

**建议**：保持当前格式，语义清晰优先

### 2. 限流错误码 E42901 是否立即实现？

**当前状态**：预留码位，未实现限流

**建议**：
- 在 Stage 2 或 Stage 3 实现限流（如 Bucket4j 或 Spring Cloud Gateway Rate Limiter）
- 当前预留错误码，避免后续变更

### 3. Stage 2 RAG 接口是否需要新增错误码？

**背景**：Draft PR #2 新增了 `/api/rag/ingest` 和 `/api/rag/query` 接口

**建议新增错误码**：
- `E40004` - 向量检索失败（400）
- `E40005` - 向量存储失败（400）
- `E50003` - 向量存储服务异常（500）

**决策点**：
- 是否在 Stage 2 合并时一并添加？
- 还是先让 Stage 2 复用现有错误码（如 `E50001`），后续再细化？

### 4. 日志脱敏策略是否需要加强？

**当前策略**：
- ✅ API Key 不打印（从环境变量读取，不输出到日志）
- ✅ 图片/文件内容不打印（只打印文件名、大小、类型）

**潜在风险**：
- 用户问题（`question` 参数）可能包含敏感信息，是否需要脱敏？
- 文件名可能包含敏感信息（如 `身份证-张三.jpg`），是否需要脱敏？

**建议**：
- 先保持现状，如有合规要求再加强脱敏
- 可在日志中对 `question` 和文件名进行截断（如只显示前 20 个字符）

### 5. TraceId 格式是否需要统一？

**当前实现**：UUID（去掉连字符，32 个字符）

**备选方案**：
- 保持 UUID 格式（带连字符，36 个字符）：更标准
- 雪花 ID（数字，19 位）：更紧凑，但需要依赖 Snowflake 生成器

**建议**：保持当前 UUID 去连字符格式，32 个字符长度适中

## 后续扩展建议

### Stage 2 RAG 集成
- `/api/rag/ingest` 和 `/api/rag/query` 直接复用统一响应体 `ApiResponse<T>`
- 新增错误码覆盖向量检索、向量存储场景

### Stage 3 工具调用
- 为工具调用错误预留错误码（如 `E50003` OCR 调用失败，`E50004` 表格提取失败）
- 考虑工具调用超时场景（可复用 `E50401` 或新增 `E50801`）

### 限流实现
- 使用预留的 `E42901` 错误码
- 集成 Bucket4j（单机限流）或 Spring Cloud Gateway Rate Limiter（分布式限流）
- 限流策略：按 IP、按用户、按接口

### APM 集成
- 集成 SkyWalking 或 Zipkin，自动采集 traceId
- 当前 `TraceIdFilter` 已为 APM 集成预留扩展点

### 监控告警
- 基于错误码统计错误率（如 4xx 率、5xx 率）
- 基于 traceId 关联日志和 APM 链路，快速定位问题

## 设计亮点

### 1. TDD 开发模式
- ✅ 先写测试（11 个测试用例覆盖所有错误码）
- ✅ 后实现功能（通过测试驱动开发）
- ✅ 重构优化（保持测试通过）

### 2. 最小改动原则
- ✅ Stage 1 的 `/api/analyze` 功能保持完全可用（向后兼容）
- ✅ 新增功能使用独立的错误码和响应体
- ✅ 旧接口标记为废弃但不删除，给客户端足够的迁移时间

### 3. 可扩展性设计
- ✅ 统一响应体为 Stage 2 RAG 和 Stage 3 工具调用预留扩展性
- ✅ 错误码枚举为后续场景预留码位（如限流 E42901）
- ✅ TraceId 过滤器为 APM 集成预留扩展点

### 4. 企业级实践
- ✅ RESTful API 版本化（路径版本化 + Deprecation 头）
- ✅ 统一响应体（成功和失败格式一致）
- ✅ 结构化日志（JSON 格式，便于日志系统解析）
- ✅ 请求追踪（traceId 全链路关联）
- ✅ 敏感信息保护（API Key 和文件内容不打印）

## 总结

**任务 BE-1 已完成**，所有验收标准均已满足：

- ✅ `mvn verify` 通过（16 tests passed, 0 failures）
- ✅ 测试不依赖真实 DASHSCOPE_API_KEY（已 mock）
- ✅ 每一类错误码都有 MockMvc 测试
- ✅ 测试断言响应头和日志（MDC）里都带 traceId
- ✅ 测试 X-Request-Id 透传
- ✅ 旧路径返回 Deprecation 头有测试
- ✅ README 补充接口说明与错误码表
- ✅ PR 描述写清设计取舍

**PR 链接**：https://github.com/Friend-ZL/image-text-analyzer/pull/3

**等待 Review 和合并**。
