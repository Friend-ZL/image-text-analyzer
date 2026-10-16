# 多模态文档分析 Agent

这是一个基于 Spring Boot 和 Spring AI 构建的智能文档分析系统，展现了现代 Java AI 应用开发的最佳实践。该项目作为技术作品集，展示了从零到一构建生产级 AI 应用的能力。

## 🎯 项目概述

本项目分四个阶段迭代开发：

- **Stage 1（已完成）**：基础多模态分析 - 图片/PDF 上传 + 视觉语言模型问答
- **Stage 2（当前阶段）**：多模态 RAG - 集成 pgvector 向量数据库，实现文档检索增强生成
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

## ✨ Stage 2 功能特性（新增）

- ✅ 集成 PostgreSQL + pgvector 向量数据库
- ✅ 文档智能切块和向量化存储
- ✅ RAG（检索增强生成）问答，带引用来源
- ✅ 基于相似度阈值的智能检索过滤
- ✅ Docker Compose 一键启动数据库环境
- ✅ 保持 Stage 1 的 `/api/analyze` 功能完整可用

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
| Embedding | 阿里云百炼 | text-embedding-v3 | 文本向量化（Stage 2） |
| 向量数据库 | PostgreSQL + pgvector | pg16 | 向量存储和检索（Stage 2） |
| ORM | Spring Data JPA | - | 数据库操作（Stage 2） |
| PDF 处理 | Apache PDFBox | 3.0.1 | PDF 渲染 |
| 容器化 | Docker Compose | - | 本地数据库环境（Stage 2） |
| 构建工具 | Maven | 3.9+ | 项目构建 |
| 测试框架 | JUnit 5 + Mockito | - | 单元/集成测试 |

## 🚀 快速开始

### 前置条件

- Java 17 或更高版本
- Maven 3.6 或更高版本
- Docker 和 Docker Compose（用于 Stage 2 的 pgvector 数据库）
- 阿里云 DashScope API Key（[获取地址](https://dashscope.aliyun.com/)）

### 1. 克隆项目

```bash
git clone <repository-url>
cd image-text-analyzer
```

### 2. 启动 pgvector 数据库（Stage 2 必需）

```bash
docker compose up -d
```

等待数据库启动完成（约 10 秒），可以通过以下命令检查状态：

```bash
docker compose ps
```

### 3. 配置 API Key

**方式一：环境变量（推荐）**

```bash
export DASHSCOPE_API_KEY="your-api-key-here"
```

**方式二：系统属性**

```bash
mvn spring-boot:run -DDASHSCOPE_API_KEY="your-api-key-here"
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

### 5. 访问界面

在浏览器中打开：http://localhost:8080

## 📖 API 使用示例

### 分析图片

```bash
curl -X POST http://localhost:8080/api/analyze \
  -F "file=@example.jpg" \
  -F "question=这张图片中的内容是什么？"
```

### 分析 PDF

```bash
curl -X POST http://localhost:8080/api/analyze \
  -F "file=@document.pdf" \
  -F "question=请总结这个文档的主要内容"
```

### 响应示例

```json
{
  "answer": "这是一张包含技术架构图的图片，展示了...",
  "model": "qwen-vl-max",
  "latencyMs": 1523,
  "metadata": {
    "fileName": "example.jpg",
    "fileType": "image",
    "fileSize": 245632,
    "contentType": "image/jpeg"
  }
}
```

## 📖 Stage 2 API 使用示例

### 入库文档（RAG）

将文档内容切块并存入向量数据库：

```bash
curl -X POST http://localhost:8080/api/rag/ingest \
  -F "file=@knowledge.txt" \
  -F "source=knowledge-base"
```

响应示例：

```json
{
  "message": "文档入库成功",
  "chunksProcessed": 15,
  "processingTimeMs": 1250
}
```

### RAG 检索增强问答

基于已入库的文档进行智能问答，返回答案和引用来源：

```bash
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Spring AI 是什么？",
    "conversationId": null
  }'
```

响应示例：

```json
{
  "answer": "Spring AI 是一个用于构建 AI 应用的框架，它提供了统一的抽象层...",
  "sources": [
    {
      "content": "Spring AI 是一个强大的框架...",
      "source": "knowledge-base",
      "similarity": 0.92,
      "metadata": {
        "fileName": "knowledge.txt",
        "contentType": "text/plain"
      }
    }
  ],
  "model": "qwen-vl-max",
  "latencyMs": 856
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

### 1. 为什么选择 Spring AI？

- **统一抽象层**：降低模型切换成本，从阿里云切换到 OpenAI 仅需修改配置
- **生态集成**：与 Spring Boot 深度集成，简化配置和依赖注入
- **OpenAI 兼容**：支持多种 LLM 厂商，避免供应商锁定

### 2. PDF 处理策略

- 使用 PDFBox 将 PDF 页面渲染为图片（150 DPI）
- 限制前 10 页以避免 Token 超限和处理延迟
- 每页独立图片，充分利用 VLM 的多图分析能力

### 3. 可扩展性设计

**分层架构**：Controller → Service → AI Client，职责清晰
- Controller：处理 HTTP 请求和参数验证
- Service：业务逻辑协调
- AI Client：封装 AI 模型调用

**配置外部化**：模型名称、API 端点、文件限制等都可通过配置文件调整

**接口抽象**：为后续阶段预留扩展点
```java
// Stage 2: RAG 扩展
interface VectorStore {
    void store(Document doc);
    List<Document> search(String query);
}

// Stage 3: 工具调用扩展
interface AgentTool {
    String execute(Map<String, Object> params);
}
```

### 4. 错误处理策略

- **参数验证前置**：文件类型、大小在服务层提前验证
- **统一异常处理**：GlobalExceptionHandler 返回标准 JSON 错误格式
- **AI 调用容错**：Spring AI 内置超时和重试机制
- **安全考虑**：API Key 仅从环境变量读取，错误信息脱敏

### 5. 测试策略

- **单元测试**：使用 Mockito 模拟依赖，测试业务逻辑
- **集成测试**：使用 `@WebMvcTest` 测试 API 端点
- **无 API Key 测试**：Mock AI 客户端，避免测试依赖外部服务

## 📁 项目结构

```
image-text-analyzer/
├── src/
│   ├── main/
│   │   ├── java/com/portfolio/analyzer/
│   │   │   ├── AnalyzerApplication.java          # 启动类
│   │   │   ├── config/
│   │   │   │   ├── AIConfig.java                # AI 配置
│   │   │   │   └── RagConfig.java               # RAG 配置（Stage 2）
│   │   │   ├── controller/
│   │   │   │   ├── AnalysisController.java      # 分析 API 控制器
│   │   │   │   └── RagController.java           # RAG API 控制器（Stage 2）
│   │   │   ├── service/
│   │   │   │   ├── DocumentAnalysisService.java # 文档分析服务
│   │   │   │   ├── ImageProcessingService.java  # 图片处理服务
│   │   │   │   ├── DocumentIngestionService.java # 文档入库服务（Stage 2）
│   │   │   │   └── RagQueryService.java         # RAG 查询服务（Stage 2）
│   │   │   ├── ai/
│   │   │   │   └── MultimodalAIClient.java      # AI 客户端封装
│   │   │   ├── dto/
│   │   │   │   ├── AnalysisResponse.java        # 分析响应 DTO
│   │   │   │   ├── IngestRequest.java           # 入库请求 DTO（Stage 2）
│   │   │   │   ├── IngestResponse.java          # 入库响应 DTO（Stage 2）
│   │   │   │   ├── RagQueryRequest.java         # RAG 查询请求 DTO（Stage 2）
│   │   │   │   └── RagQueryResponse.java        # RAG 查询响应 DTO（Stage 2）
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java  # 全局异常处理
│   │   │       ├── InvalidFileTypeException.java
│   │   │       ├── FileSizeLimitException.java
│   │   │       └── AIServiceException.java
│   │   └── resources/
│   │       ├── application.yml                   # 应用配置
│   │       └── static/
│   │           └── index.html                    # 前端页面
│   └── test/
│       ├── java/com/portfolio/analyzer/
│       │   ├── service/
│       │   │   ├── ImageProcessingServiceTest.java
│       │   │   ├── DocumentAnalysisServiceTest.java
│       │   │   ├── DocumentIngestionServiceTest.java # 入库服务测试（Stage 2）
│       │   │   └── RagQueryServiceTest.java     # RAG 查询测试（Stage 2）
│       │   ├── ai/
│       │   │   └── MultimodalAIClientTest.java
│       │   ├── controller/
│       │   │   ├── AnalysisControllerTest.java
│       │   │   └── RagControllerTest.java       # RAG 控制器测试（Stage 2）
│       │   └── TestDataGenerator.java           # 测试数据生成器
│       └── resources/
│           ├── test-data/                        # 测试数据
│           └── application-test.yml              # 测试配置
├── docker-compose.yml                            # pgvector 数据库（Stage 2）
├── pom.xml
├── .gitignore
├── README.md
└── 文档-多模态文档分析Agent设计.md          # 详细设计文档
```

## 🗺 开发路线图

### Stage 2: 多模态 RAG（已完成）

- ✅ 集成 PostgreSQL + pgvector 向量数据库
- ✅ 实现文档分块和向量化（文本）
- ✅ 构建检索增强生成（RAG）流程
- ✅ 基于相似度阈值的智能检索
- ✅ Docker Compose 本地数据库环境
- ✅ 完整的单元测试和集成测试

**技术要点**：
- 使用 Spring AI PgVectorStore 进行向量存储
- TokenTextSplitter 实现智能文档切块
- 相似度计算和阈值过滤
- 保持 Stage 1 功能完全兼容

### Stage 3: Agent 工具调用（计划中）

- [ ] OCR 工具：Tess4J 提取图片中的文字
- [ ] 表格提取：识别表格结构并转为结构化数据
- [ ] 计算器：处理数学表达式计算
- [ ] 图表生成：根据数据生成可视化图表

**技术要点**：
- Spring AI 的 Function Calling 机制
- 工具调用的可观测性（tracing）
- 多工具协同决策

### Stage 4: 评估与优化（计划中）

- [ ] 构建测试集并评估准确率
- [ ] 添加缓存机制（Redis）减少重复调用
- [ ] 实现批处理提高吞吐量
- [ ] 添加监控和日志（Prometheus + Grafana）
- [ ] Docker 容器化和云部署

**技术要点**：
- 使用 LangSmith/Weights & Biases 进行评估
- Redis 缓存策略
- 容器化部署最佳实践

## 🔐 安全说明

- ⚠️ **切勿将 API Key 提交到代码仓库**
- ✅ 始终使用环境变量或密钥管理服务
- ✅ 文件上传限制：最大 10MB，仅支持图片和 PDF
- ✅ 错误信息中不包含敏感信息（API Key、内部路径等）

## 📝 配置说明

### 核心配置项（application.yml）

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1  # API 端点
      api-key: ${DASHSCOPE_API_KEY}                                # API Key
      chat:
        options:
          model: qwen-vl-max                                       # 聊天模型
          temperature: 0.7                                         # 温度参数
      embedding:
        options:
          model: text-embedding-v3                                 # Embedding 模型（Stage 2）
    vectorstore:
      pgvector:
        index-type: HNSW                                           # 索引类型（Stage 2）
        distance-type: COSINE_DISTANCE                             # 距离度量（Stage 2）
        dimensions: 1536                                           # 向量维度（Stage 2）
  datasource:
    url: jdbc:postgresql://localhost:5432/analyzer_db              # 数据库连接（Stage 2）
    username: analyzer                                             # 数据库用户名（Stage 2）
    password: analyzer_pass                                        # 数据库密码（Stage 2）

app:
  analysis:
    max-pdf-pages: 10           # PDF 最大页数
    max-file-size-mb: 10        # 文件最大大小（MB）
    pdf-render-dpi: 150         # PDF 渲染 DPI
  rag:
    chunk-size: 500             # 文档切块大小（Stage 2）
    chunk-overlap: 50           # 切块重叠大小（Stage 2）
    top-k: 3                    # 检索返回数量（Stage 2）
    similarity-threshold: 0.7   # 相似度阈值（Stage 2）
```

### 切换到 OpenAI GPT-4o

修改 `application.yml`：

```yaml
spring:
  ai:
    openai:
      base-url: https://api.openai.com/v1
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
```

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

## 📧 联系方式

- 项目作者：[您的名字]
- 邮箱：[您的邮箱]
- GitHub：[您的 GitHub]

---

**⭐ 如果这个项目对你有帮助，请给一个 Star！**
