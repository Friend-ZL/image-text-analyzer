# 多模态文档分析 Agent

这是一个基于 Spring Boot 和 Spring AI 构建的智能文档分析系统，展现了现代 Java AI 应用开发的最佳实践。该项目作为技术作品集，展示了从零到一构建生产级 AI 应用的能力。

## 🎯 项目概述

本项目分四个阶段迭代开发：

- **Stage 1（当前阶段）**：基础多模态分析 - 图片/PDF 上传 + 视觉语言模型问答
- **Stage 2**：多模态 RAG - 集成 pgvector 向量数据库，实现文档检索增强生成
- **Stage 3**：Agent 工具调用 - 集成 OCR、表格提取、计算器、图表生成等工具
- **Stage 4**：评估与优化 - 准确率测试、性能优化、监控告警、容器化部署

## ✨ Stage 1 功能特性

- ✅ 支持多种图片格式（JPEG、PNG、GIF、WebP）
- ✅ 支持 PDF 文档分析（自动渲染为图片）
- ✅ 基于阿里云百炼 qwen-vl-max 视觉语言模型
- ✅ OpenAI 兼容接口，可一键切换至 GPT-4o
- ✅ 拖拽式上传的现代化前端界面
- ✅ 完整的错误处理和参数验证
- ✅ 单元测试和集成测试覆盖

## 🏗 系统架构

```mermaid
graph TB
    subgraph "前端层"
        UI[静态HTML页面<br/>拖拽上传 + 问题输入]
    end
    
    subgraph "控制器层"
        Controller[AnalysisController<br/>POST /api/analyze]
    end
    
    subgraph "服务层"
        Service[DocumentAnalysisService]
        ImageService[ImageProcessingService]
    end
    
    subgraph "AI集成层"
        AIClient[MultimodalAIClient<br/>Spring AI ChatClient]
        Config[AI配置<br/>application.yml + 环境变量]
    end
    
    subgraph "外部服务"
        DashScope[阿里云百炼<br/>qwen-vl-max]
    end
    
    UI -->|multipart/form-data| Controller
    Controller --> Service
    Service --> ImageService
    Service --> AIClient
    ImageService -->|PDF转图片| Service
    AIClient --> Config
    AIClient -->|OpenAI兼容API| DashScope
    
    style UI fill:#e1f5ff
    style Service fill:#fff3e0
    style AIClient fill:#f3e5f5
    style DashScope fill:#e8f5e9
```

## 🔧 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 核心框架 | Spring Boot | 3.2.0 | Web 应用框架 |
| 语言 | Java | 17+ | 开发语言 |
| AI 集成 | Spring AI | 1.0.0-M3 | AI 模型调用抽象层 |
| LLM 服务 | 阿里云百炼 | qwen-vl-max | 视觉语言模型 |
| PDF 处理 | Apache PDFBox | 3.0.1 | PDF 渲染 |
| 构建工具 | Maven | 3.9+ | 项目构建 |
| 测试框架 | JUnit 5 + Mockito | - | 单元/集成测试 |

## 🚀 快速开始

### 前置条件

- Java 17 或更高版本
- Maven 3.6 或更高版本
- 阿里云 DashScope API Key（[获取地址](https://dashscope.aliyun.com/)）

### 1. 克隆项目

```bash
git clone <repository-url>
cd image-text-analyzer
```

### 2. 配置 API Key

**方式一：环境变量（推荐）**

```bash
export DASHSCOPE_API_KEY="your-api-key-here"
```

**方式二：系统属性**

```bash
mvn spring-boot:run -DDASHSCOPE_API_KEY="your-api-key-here"
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

### 4. 访问界面

在浏览器中打开：http://localhost:8080

## 🐳 Docker 本地开发

### 使用 Docker Compose 快速启动

如果你更喜欢使用 Docker 进行本地开发，可以使用 docker-compose 一键启动所有服务（包括 PostgreSQL、Redis、MinIO、Keycloak）。

#### 1. 复制环境变量模板

```bash
cp .env.example .env
```

#### 2. 编辑 .env 文件

打开 `.env` 文件，至少需要设置：
- `DASHSCOPE_API_KEY`：你的阿里云 DashScope API Key（必填）
- 其他密码可以保持默认值（仅用于本地开发）

#### 3. 启动所有服务

```bash
docker compose up -d
```

首次启动会构建应用镜像，可能需要几分钟时间。

#### 4. 查看服务状态

```bash
docker compose ps
```

所有服务应该显示为 `healthy` 状态。

#### 5. 访问应用

- **应用界面**: http://localhost:8080
- **MinIO Console**: http://localhost:9001（用户名/密码见 `.env`）
- **Keycloak Admin**: http://localhost:8180（用户名/密码见 `.env`）

#### 6. 查看日志

```bash
# 查看应用日志
docker compose logs -f app

# 查看所有服务日志
docker compose logs -f
```

#### 7. 停止服务

```bash
docker compose down
```

### 仅启动依赖服务（便于 IDE 调试）

如果你想在 IDE 中调试应用，可以只启动依赖服务：

```bash
# 只启动 PostgreSQL、Redis、MinIO、Keycloak
docker compose up -d postgres redis minio keycloak

# 在 IDE 中启动 Spring Boot 应用
mvn spring-boot:run
```

这样可以方便地在 IDE 中设置断点和调试代码。

### Docker 服务说明

| 服务 | 端口 | 说明 |
|------|------|------|
| 应用 | 8080 | Spring Boot 应用 |
| PostgreSQL | 5432 | 数据库（带 pgvector 扩展，为 Stage 2 准备） |
| Redis | 6379 | 缓存服务（为 Stage 4 准备） |
| MinIO | 9000, 9001 | 对象存储（为未来文件存储准备） |
| Keycloak | 8180 | 身份认证服务（为未来权限管理准备） |

**注意**：所有端口都绑定到 `127.0.0.1`，仅本地访问，不会暴露到外网。

## 📖 API 使用示例

### 分析图片

```bash
curl -X POST http://localhost:8080/api/analyze \
## 📖 API 接口规范

### 接口概览

| 接口路径 | 方法 | 说明 | 状态 |
|---------|------|------|------|
| `/api/v1/analyze` | POST | 文档分析接口（当前版本） | ✅ 推荐使用 |
| `/api/analyze` | POST | 文档分析接口（旧版本） | ⚠️ 已废弃 |
| `/api/health` | GET | 健康检查接口 | ✅ 正常 |

### 统一响应体格式

所有 API 接口均返回统一的响应体结构：

```json
{
  "code": "00000",
  "message": "成功",
  "data": { ... },
  "traceId": "a1b2c3d4e5f6..."
}
```

**字段说明：**
- `code`：错误码，成功为 `"00000"`，错误格式为 `E + HTTP状态码 + 序号`
- `message`：响应消息，成功或错误描述
- `data`：业务数据，成功时返回，失败时为 `null`
- `traceId`：请求追踪 ID，用于日志关联和问题排查

### 请求追踪

每个请求都会自动生成一个 `traceId`，用于关联请求日志：

- **自动生成**：如果请求头中没有 `X-Request-Id`，系统会自动生成一个 UUID
- **透传机制**：如果请求头包含 `X-Request-Id`，系统会原样透传作为 traceId
- **响应头返回**：响应头 `X-Trace-Id` 会返回本次请求的 traceId
- **日志关联**：所有日志中都会包含 traceId，便于问题排查

### API 使用示例

#### 1. 分析图片（推荐使用 v1 接口）

```bash
curl -X POST http://localhost:8080/api/v1/analyze \
  -H "X-Request-Id: my-custom-trace-id-12345" \
  -F "file=@example.jpg" \
  -F "question=这张图片中的内容是什么？"
```

**成功响应示例：**

```json
{
  "code": "00000",
  "message": "成功",
  "data": {
    "answer": "这是一张包含技术架构图的图片，展示了...",
    "model": "qwen-vl-max",
    "latencyMs": 1523,
    "metadata": {
      "fileName": "example.jpg",
      "fileType": "image",
      "fileSize": 245632,
      "contentType": "image/jpeg"
    }
  },
  "traceId": "my-custom-trace-id-12345"
}
```

#### 2. 分析 PDF

```bash
curl -X POST http://localhost:8080/api/v1/analyze \
  -F "file=@document.pdf" \
  -F "question=请总结这个文档的主要内容"
```

#### 3. 使用旧版接口（已废弃）

```bash
curl -X POST http://localhost:8080/api/analyze \
  -F "file=@example.jpg" \
  -F "question=测试"
```

**响应头会包含废弃提示：**

```
Deprecation: true
Sunset: Thu, 31 Dec 2026 23:59:59 GMT
Link: </api/v1/analyze>; rel="successor-version"
```

### 错误码表

#### 错误码规则

**重要**：错误码一旦发布不得修改含义、不得复用。本表即接口契约。

- 错误码格式：`E + HTTP状态码 + 两位序号`（如 `E40001`）
- 成功码：`00000`
- 新增错误码应按分区顺序添加，预留码位由后续 PR 填充

#### 成功响应

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| 00000 | 200 | 成功 |

#### 4xx 客户端错误

| 错误码 | HTTP 状态 | 说明 | 常见原因 |
|--------|----------|------|---------|
| E40001 | 400 | 参数校验失败 | 必填参数缺失、格式不正确 |
| E40002 | 400 | 文件不能为空 | 未上传文件或文件为空 |
| E40003 | 400 | 问题不能为空 | question 参数为空或空白字符串 |
| E40401 | 404 | 请求的资源不存在 | 访问不存在的端点 |
| E40501 | 405 | 不支持的请求方法 | 使用了不支持的 HTTP 方法（响应包含 `Allow` 头） |
| E41301 | 413 | 文件大小超过限制 | 文件大小超过配置的上限 |
| E41501 | 415 | 不支持的文件类型 | 文件类型不是支持的图片或 PDF 格式，或非 multipart 请求 |
| E42901 | 429 | 请求过于频繁 | 触发限流（预留，当前未启用） |

#### 5xx 服务端错误

| 错误码 | HTTP 状态 | 说明 | 常见原因 |
|--------|----------|------|---------|
| E50001 | 500 | 服务器内部错误 | 未预期的系统异常 |
| E50002 | 500 | AI服务调用失败 | 模型服务调用失败 |
| E50201 | 502 | 模型服务异常 | 上游模型服务不可用 |
| E50401 | 504 | 模型调用超时 | 模型响应时间过长（包括整条异常链中的明确超时类型） |

**超时类型说明**：遍历整条异常链，只认以下明确的超时类型：
- `java.util.concurrent.TimeoutException`
- `java.net.SocketTimeoutException`
- `java.net.http.HttpTimeoutException`（含 `HttpConnectTimeoutException`）
- `io.netty.handler.timeout.ReadTimeoutException`（仅在 classpath 存在时）

**注意**：
- 错误响应中的 `message` 字段只返回固定文案，不会透传上游服务的原始错误信息。详细错误信息仅记录在日志中。
- 405 响应会在 `Allow` 头中列出支持的 HTTP 方法。

### 错误响应示例

#### 参数校验失败

```json
{
  "code": "E40001",
  "message": "文件不能为空",
  "data": null,
  "traceId": "abc123def456"
}
```

#### 文件过大

```json
{
  "code": "E41301",
  "message": "文件大小超过10MB限制",
  "data": null,
  "traceId": "xyz789uvw012"
}
```

#### 不支持的文件类型

```json
{
  "code": "E41501",
  "message": "不支持的文件类型: text/plain",
  "data": null,
  "traceId": "qwe456rty789"
}
```

#### AI 服务错误

```json
{
  "code": "E50002",
  "message": "模型服务暂时不可用",
  "data": null,
  "traceId": "asd123fgh456"
}
```

## 🧪 测试

### 运行所有测试

```bash
mvn test
```

### 运行集成测试

```bash
mvn verify
```

### 测试覆盖

- ✅ 图片处理服务单元测试
- ✅ PDF 处理服务单元测试
- ✅ AI 客户端参数验证测试
- ✅ 文档分析服务集成测试
- ✅ API 控制器集成测试

## 🔍 核心设计决策（面试要点）

### 1. API 规范底座设计

#### 1.1 为什么需要统一响应体？

**问题背景**：原始实现中，成功返回业务数据，失败返回 `{error, message, timestamp}`，格式不统一，客户端需要多种解析逻辑。

**设计方案**：`{code, message, data, traceId}` 统一格式

- **统一性**：成功和失败都用相同结构，客户端解析逻辑统一
- **可扩展性**：为后续 RAG 接口（`/api/rag/*`）也能直接复用
- **可追踪性**：每个响应都带 traceId，便于日志关联和问题排查

#### 1.2 错误码设计原则

**错误码格式**：`E + HTTP状态码 + 两位序号`，例如 `E40001`

- **语义清晰**：错误码直接体现 HTTP 状态，易于理解
- **分类管理**：按 4xx（客户端）和 5xx（服务端）分类
- **可扩展**：每个状态码预留 99 个错误码位，足够扩展

**HTTP 状态映射策略**：
- 参数错误 → 400 Bad Request
- 文件过大 → 413 Payload Too Large
- 不支持的类型 → 415 Unsupported Media Type
- 模型超时 → 504 Gateway Timeout
- AI 服务错误 → 500/502（区分内部错误和上游错误）

**优势**：客户端可以根据 HTTP 状态快速判断错误类别，再根据错误码精确定位问题。

#### 1.3 请求追踪（TraceId）设计

**实现方式**：Filter + MDC（Mapped Diagnostic Context）

- **优先透传**：从请求头 `X-Request-Id` 获取，没有则生成 UUID
- **全链路记录**：放入 MDC，所有日志自动带上 traceId
- **响应头返回**：通过 `X-Trace-Id` 返回给客户端

**为什么不用 Spring Cloud Sleuth？**
- 本项目是单体应用，不需要分布式追踪的复杂性
- 自实现 Filter 只需 30 行代码，轻量高效
- 为后续集成 APM（如 SkyWalking）预留了扩展点

#### 1.4 日志编码器选择

**选择 logstash-logback-encoder**：

- **结构化日志**：JSON 格式，便于日志收集系统（ELK、Loki）解析
- **MDC 集成**：自动包含 traceId 等上下文信息
- **Profile 切换**：开发环境可读格式，生产环境 JSON，提升开发体验

**敏感信息保护**：
- API Key 只从环境变量读取，日志中不输出
- 文件内容和图片 base64 不打印到日志

#### 1.5 版本化策略

**方案**：路径版本化（`/api/v1/analyze`）

- **简单直观**：版本号在 URL 中，一目了然
- **易于路由**：Spring MVC 原生支持，无需额外配置
- **兼容平滑**：旧版本保留并返回 `Deprecation` 头，给客户端足够的迁移时间

**为什么不用 Header 或 Query 参数版本化？**
- Header 版本化对开发者不友好，调试时不直观
- Query 参数版本化容易被遗漏，且不符合 RESTful 规范

**废弃策略**：
- `Deprecation: true` - 标记接口已废弃
- `Sunset: <date>` - 明确下线时间
- `Link: <new-url>; rel="successor-version"` - 指向新版本

### 2. 为什么选择 Spring AI？

- **统一抽象层**：降低模型切换成本，从阿里云切换到 OpenAI 仅需修改配置
- **生态集成**：与 Spring Boot 深度集成，简化配置和依赖注入
- **OpenAI 兼容**：支持多种 LLM 厂商，避免供应商锁定

### 3. PDF 处理策略

- 使用 PDFBox 将 PDF 页面渲染为图片（150 DPI）
- 限制前 10 页以避免 Token 超限和处理延迟
- 每页独立图片，充分利用 VLM 的多图分析能力

### 4. 可扩展性设计

**分层架构**：Controller → Service → AI Client，职责清晰
- Controller：处理 HTTP 请求和参数验证
- Service：业务逻辑协调
