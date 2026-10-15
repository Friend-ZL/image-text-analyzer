# 多模态文档分析Agent - 设计文档

## 项目概述

这是一个基于Java + Spring Boot的作品集项目，展示现代AI应用开发能力。项目分四个阶段迭代：
- **Stage 1（当前）**：基础多模态分析 - 图片/PDF上传 + 视觉语言模型问答
- Stage 2：多模态RAG - 集成pgvector向量数据库
- Stage 3：Agent工具调用 - OCR、表格提取、计算器、图表生成
- Stage 4：评估与优化

## Stage 1 架构设计

### 技术栈
- **核心框架**：Spring Boot 3.x + Java 17
- **AI集成**：Spring AI (OpenAI-compatible chat client)
- **LLM服务**：阿里云百炼/DashScope (qwen-vl-max模型)
- **PDF处理**：Apache PDFBox
- **构建工具**：Maven
- **测试**：JUnit 5 + Mockito + Spring Boot Test

### 架构图

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

### 核心模块设计

#### 1. 配置模块 (config/)
```
AIConfig
- 读取环境变量 DASHSCOPE_API_KEY
- 配置 Spring AI ChatClient
- 支持模型/端点切换（application.yml）
```

#### 2. 控制器模块 (controller/)
```
AnalysisController
- POST /api/analyze
  - 接收参数：MultipartFile file, String question
  - 验证：文件类型（image/*, application/pdf）、大小限制（10MB）
  - 返回：AnalysisResponse (answer, model, latencyMs, metadata)
```

#### 3. 服务模块 (service/)
```
DocumentAnalysisService（主服务）
- analyzeDocument(file, question) → AnalysisResult
- 协调图片处理和AI调用

ImageProcessingService
- processImage(MultipartFile) → List<byte[]>
- processPdf(MultipartFile) → List<byte[]>（限制前10页）
- 使用PDFBox渲染PDF为图片
```

#### 4. AI客户端 (ai/)
```
MultimodalAIClient
- analyzeImages(images, question) → String
- 封装Spring AI ChatClient
- 支持多图片输入
- 错误处理和重试
```

#### 5. DTO模块 (dto/)
```
AnalysisRequest
- MultipartFile file
- String question

AnalysisResponse
- String answer
- String model
- long latencyMs
- Map<String, Object> metadata
```

#### 6. 异常处理
```
GlobalExceptionHandler
- InvalidFileTypeException
- FileSizeLimitException
- AIServiceException
- 返回统一错误格式
```

### 配置文件设计

**application.yml**
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-vl-max

app:
  analysis:
    max-pdf-pages: 10
    supported-image-types: image/jpeg,image/png,image/gif,image/webp
    supported-file-types: image/jpeg,image/png,image/gif,image/webp,application/pdf
```

### 前端设计

**单页面HTML（resources/static/index.html）**
- 拖拽区域（支持图片和PDF）
- 文件预览（图片显示缩略图，PDF显示图标）
- 问题输入框（多行文本）
- 提交按钮
- 结果展示区域（答案 + 元数据）
- 加载状态指示
- 中文UI

### 测试策略

#### 单元测试
- `ImageProcessingServiceTest`：测试图片/PDF处理逻辑
- `MultimodalAIClientTest`：Mock ChatClient测试AI调用
- `DocumentAnalysisServiceTest`：集成服务层测试

#### 集成测试
- `AnalysisControllerTest`：
  - Mock服务层
  - 测试文件上传
  - 测试验证逻辑
  - 测试错误处理

#### 测试数据
- 准备测试图片（test.jpg）
- 准备测试PDF（test.pdf）
- Mock AI响应数据

### 关键设计决策（面试要点）

#### 1. 为什么选择Spring AI？
- 统一抽象层，降低模型切换成本
- OpenAI兼容协议支持多厂商（阿里云、OpenAI、Azure）
- Spring生态集成，简化配置和依赖注入

#### 2. PDF处理策略
- 使用PDFBox将PDF页面渲染为图片（DPI 150）
- 限制10页避免Token超限和处理延迟
- 每页独立图片，利用VLM多图能力

#### 3. 可扩展性设计
- **服务分层**：Controller → Service → AI Client，各层职责清晰
- **配置外部化**：模型、端点、限制都可配置
- **接口抽象**：为Stage 2的RAG和Stage 3的工具调用预留扩展点
  ```java
  // 未来扩展
  interface DocumentProcessor {
      List<byte[]> process(MultipartFile file);
  }
  
  interface AIAgent {
      String analyze(AnalysisContext context);
  }
  ```

#### 4. 错误处理策略
- 文件验证前置（类型、大小）
- AI调用超时和重试（Spring AI内置）
- 统一异常处理返回标准JSON
- 敏感信息脱敏（API Key不输出）

#### 5. 安全考虑
- API Key仅从环境变量读取
- 文件类型白名单验证
- 文件大小限制
- 上传文件临时存储（不持久化敏感数据）

### 项目结构
```
image-text-analyzer/
├── src/
│   ├── main/
│   │   ├── java/com/portfolio/analyzer/
│   │   │   ├── AnalyzerApplication.java
│   │   │   ├── config/
│   │   │   │   └── AIConfig.java
│   │   │   ├── controller/
│   │   │   │   └── AnalysisController.java
│   │   │   ├── service/
│   │   │   │   ├── DocumentAnalysisService.java
│   │   │   │   └── ImageProcessingService.java
│   │   │   ├── ai/
│   │   │   │   └── MultimodalAIClient.java
│   │   │   ├── dto/
│   │   │   │   ├── AnalysisRequest.java
│   │   │   │   └── AnalysisResponse.java
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java
│   │   │       ├── InvalidFileTypeException.java
│   │   │       ├── FileSizeLimitException.java
│   │   │       └── AIServiceException.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-test.yml
│   │       └── static/
│   │           └── index.html
│   └── test/
│       ├── java/com/portfolio/analyzer/
│       │   ├── service/
│       │   │   ├── ImageProcessingServiceTest.java
│       │   │   └── DocumentAnalysisServiceTest.java
│       │   ├── ai/
│       │   │   └── MultimodalAIClientTest.java
│       │   └── controller/
│       │       └── AnalysisControllerTest.java
│       └── resources/
│           ├── test-data/
│           │   ├── test.jpg
│           │   └── test.pdf
│           └── application-test.yml
├── pom.xml
├── .gitignore
└── README.md
```

### Stage 2-4 路线图（简要）

#### Stage 2: 多模态RAG
- 集成pgvector（PostgreSQL）
- 文档分块和向量化（文本+图片）
- 检索增强生成
- 历史对话管理

#### Stage 3: Agent工具调用
- OCR工具：Tess4J提取文字
- 表格提取：表格识别和结构化
- 计算器：数学表达式计算
- 图表生成：基于数据生成可视化

#### Stage 4: 评估与优化
- 准确率评估（测试集）
- 性能优化（缓存、批处理）
- 监控和日志
- 部署方案（Docker + 云服务）

## 实现检查清单

### 功能完成
- [ ] Maven项目结构创建
- [ ] Spring Boot应用启动类
- [ ] AI配置（Spring AI + DashScope）
- [ ] 图片处理服务
- [ ] PDF处理服务（PDFBox）
- [ ] AI客户端（Spring AI封装）
- [ ] 文档分析服务
- [ ] API控制器（/api/analyze）
- [ ] 请求/响应DTO
- [ ] 异常处理
- [ ] 前端页面（拖拽上传）

### 测试完成
- [ ] 图片处理单元测试
- [ ] PDF处理单元测试
- [ ] AI客户端测试（Mock）
- [ ] 服务层测试
- [ ] 控制器集成测试
- [ ] mvn verify通过

### 文档完成
- [ ] README.md（中文）
  - [ ] 项目介绍
  - [ ] 架构图
  - [ ] 本地运行指南
  - [ ] API示例（curl）
  - [ ] Stage 2-4路线图
- [ ] .gitignore更新

### 代码质量
- [ ] 代码注释（关键逻辑）
- [ ] 异常处理完善
- [ ] 配置项文档化
- [ ] 日志记录（INFO级别）

---

**最后更新时间**: 2026-10-15  
**当前阶段**: Stage 1开发中
