# 智能教育助手（AI 对话服务）

基于 Spring AI 构建的教育场景智能对话服务。用户可以和 AI 助手对话，让它**推荐课程、查询课程详情、预下单**。

> 本仓库只包含 AI 服务模块（`tj-aigc`）的源码，用于展示该模块的实现。
> 它原本是一个 Spring Cloud 微服务项目中的独立服务，依赖的公共模块和业务服务未包含在内。

---

## 技术栈

| 分类 | 技术 |
|---|---|
| 框架 | Spring Boot 3.3、Spring AI 1.0、Spring Cloud Alibaba |
| 注册/配置中心 | Nacos |
| 服务调用 | OpenFeign |
| 大模型 | 阿里云百炼（DashScope）通义千问 |
| 向量存储 | Elasticsearch（dense_vector） |
| 会话存储 | Redis / MySQL / MongoDB / PostgreSQL（可配置切换） |
| 流式响应 | WebFlux + SSE |
| 其他 | MyBatis-Plus、Hutool |

---

## 核心功能

### 1. 多智能体路由架构

把对话能力拆成 **1 个路由智能体 + 4 个业务智能体**：

```
用户提问
   ↓
【路由智能体】只做意图分类，不挂工具
   输出：RECOMMEND / CONSULT / BUY / KNOWLEDGE
   ↓ 由代码按类型从 Spring 容器取对应智能体
┌──────────┬──────────┬──────────┬──────────┐
↓          ↓          ↓          ↓
课程推荐    课程咨询    课程购买    知识讲解
(RAG检索)  (咨询)     (预下单)   (知识检索)
```

**为什么这么拆**：如果只用一个智能体承载全部能力，工具数量增多后模型容易选错工具，系统提示词也会膨胀到难以维护。拆分后每个智能体只挂自己领域的工具、只维护自己领域的提示词。

**工程实现**：抽出 `AbstractAgent` 模板类，统一收敛流式输出、SSE 事件封装、生成状态管理、中断处理等公共逻辑；每个具体智能体只需声明自己的类型标识、系统提示词、所需工具与 Advisor。新增一类业务只需新增一个类，路由与分派代码无需改动。

### 2. RAG 检索增强

课程知识库经 Embedding 模型向量化后写入 Elasticsearch 向量索引（1024 维）。对话时按语义相似度检索（topK=6、相似度阈值 0.6），将命中的课程资料注入提示词，使回答基于真实课程数据，降低模型幻觉；知识库更新时无需重新训练模型。

### 3. Function Calling 工具调用

通过 `@Tool` 注解把「按 ID 查询课程详情」「预下单」等接口封装为可调用工具。模型根据用户意图自行决定调用哪个工具，Spring AI 在本地反射执行对应的 Java 方法，再把结果回传给模型生成最终回答。

工具所需的用户身份（`userId`）与请求标识（`requestId`）通过 Spring AI 的 `ToolContext` 传入，工具内部据此设置用户上下文，以便后续通过 Feign 调用业务服务时通过鉴权。

### 4. 流式响应中的结构化数据下发

**问题**：工具查询到的课程价格、有效期等结构化数据需要传给前端渲染成卡片，但流式响应中只有模型的文本输出，没有结构化数据的传输通道。

**解决**：

1. 请求开始时生成 `requestId`，通过 `toolContext` 传给工具；
2. 工具执行后，将结果按 `requestId` 存入内存容器（`ToolResultHolder`）；
3. 流式响应结束前，从容器取出数据，以自定义 SSE 事件类型单独下发。

**自定义事件协议**：

| 事件类型 | 含义 |
|---|---|
| `1001` | 文本内容（前端逐字渲染） |
| `1002` | 生成停止 |
| `1003` | 业务参数（前端渲染卡片） |

相比「让模型直接输出 JSON 再解析」，该方案的数据从数据库原样下发、不经过模型改写，格式稳定且节省 token。

### 5. 多轮会话记忆持久化

基于 Spring AI 的 `MessageWindowChatMemory` 维护滑动窗口（上限 100 条消息），避免上下文无限增长。自定义 `ChatMemoryRepository` 实现，把会话消息序列化后存入外部存储，解决了默认内存实现重启丢失、无法多实例共享的问题。

实现了 **Redis、MySQL、MongoDB、PostgreSQL** 四种存储，通过 `@ConditionalOnProperty` 按配置装配，切换存储介质无需修改代码。会话标识使用 `用户ID_会话ID` 结构，天然实现用户间隔离。

### 6. 系统提示词热更新

系统提示词托管在 Nacos 配置中心（共 7 份：通用对话、路由、推荐、咨询、购买、知识讲解、文本处理）。每个提示词由 `AtomicReference` 持有，服务启动时加载并注册 Nacos 监听器，配置变更后即时生效，**无需重启服务**。

### 7. 会话管理

| 接口 | 功能 |
|---|---|
| `POST /session` | 创建会话（可选是否生成 AI 欢迎语） |
| `GET /session/{sessionId}` | 查询会话消息记录 |
| `GET /session/history` | 分页查询历史会话 |
| `DELETE /session/history` | 删除历史会话 |
| `PUT /session/history` | 修改会话标题 |
| `GET /session/hot` | 获取热门示例问题 |

### 8. 对外文本处理能力

`POST /chat/text` 是一个**无状态**的文本处理接口，不涉及会话记忆与 RAG 检索，供其他业务服务调用。项目中的学习服务（`tj-learning`）在学员提交提问后通过 Feign 调用该接口，由 AI 生成自动回复。

配套的工程处理：

- **异步执行**：单次生成耗时较长，使用 `@Async` 避免阻塞学员提交提问；
- **降级容错**：Feign 配置 fallback，AI 服务异常时返回兜底内容，避免 AI 故障导致提问功能整体失败。

---

## 项目结构

```
com.tianji.aigc
├── agent/                      多智能体
│   ├── Agent.java              智能体接口
│   ├── AbstractAgent.java      模板类：统一流式输出与事件封装
│   ├── RouteAgent.java         路由智能体
│   ├── RecommendAgent.java     课程推荐智能体
│   ├── ConsultAgent.java       课程咨询智能体
│   ├── BuyAgent.java           课程购买智能体
│   └── KnowledgeAgent.java     知识讲解智能体
├── advisor/
│   └── RecordOptimizationAdvisor.java   记忆写入后清理中间结果
├── config/
│   ├── SpringAIConfig.java      ChatClient / ChatMemory 装配
│   ├── SystemPromptConfig.java  提示词加载与热更新
│   ├── AIProperties.java        配置项
│   └── ToolResultHolder.java    工具结果暂存容器
├── memory/                     会话记忆存储
│   ├── ChatMemoryRepository 的四种实现
│   │   ├── RedisChatMemoryRepository.java
│   │   ├── jdbc/JdbcChatMemoryRepository.java
│   │   ├── mongodb/MongoDBChatMemoryRepository.java
│   │   └── pg/PgChatMemoryRepository.java
│   └── MyMessage.java           消息序列化中间结构
├── tools/                      工具定义
│   ├── CourseTools.java         课程查询、课程详情
│   ├── OrderTools.java          预下单
│   └── result/                  工具返回值结构
├── controller/
│   ├── ChatController.java      对话（SSE 流式）、停止、文本处理、提示词模板
│   ├── SessionController.java   会话管理
│   └── EmbeddingController.java 向量库写入、检索、删除
├── enums/
│   ├── AgentTypeEnum.java       智能体类型
│   ├── ChatEventTypeEnum.java   SSE 事件类型
│   └── MessageTypeEnum.java     消息类型
├── service/                    业务服务接口与实现
└── vo/ dto/ entity/ mapper/    数据模型与持久层
```

---

## 实现要点

- **多智能体路由**：路由只负责意图分类、不挂工具；业务智能体各自持有独立的提示词与工具，通过 Spring 容器动态分派，新增业务类型无需改动分发逻辑。
- **结构化数据下发**：以 `requestId` 关联请求与工具结果，流式末尾通过自定义 SSE 事件下发业务数据，实现「一次请求返回文本 + 结构化数据」。
- **记忆存储可配置**：抽象出 `ChatMemoryRepository` 的四种实现，用条件注解按配置装配；自定义消息序列化结构解决 `Message` 接口反序列化丢失类型的问题。
- **提示词热更新**：`AtomicReference` + Nacos 监听器，保证多线程可见性并支持不重启生效。
- **中间结果隔离**：自定义 Advisor 在记忆写入后清理路由等内部中间结果，避免内部标识污染用户可见的会话历史。

---

## 本地运行

### 依赖的中间件

| 组件 | 用途 |
|---|---|
| MySQL | 会话、消息记录持久化 |
| Redis | 会话记忆存储（默认） |
| Elasticsearch | 向量存储 |
| Nacos | 配置中心（存放系统提示词） |

### 需要的配置

在 Nacos 中配置以下内容后启动服务：

```yaml
# 大模型接入
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}     # 需自行申请阿里云百炼 API Key
      chat:
        options:
          model: qwen-plus

# 会话记忆存储类型
tj:
  ai:
    memory:
      max: 100
      type: Redis                        # Redis / MYSQL / MongoDB / PostgreSQL
    chat-type: ROUTE                     # ROUTE / ENHANCE
```

系统提示词文件（`system-chat-message.txt`、`route-agent-system-message.txt` 等）需要上传到 Nacos 配置中心。

### 启动

```bash
mvn spring-boot:run
```

服务端口：`8094`

---

## 说明

本项目为学习实践项目，在实现过程中重点关注了 AI 能力在业务系统中的工程化落地：流式协议设计、结构化数据通道、会话状态持久化、配置化切换与降级容错。
