# 风云WindCloud · 大龙瞎

> AI 驱动的操作系统代理 —— 能直接读写文件、执行命令、上网搜索的智能体

---

## 项目简介

大龙瞎是一个基于 Spring Boot 3 的 AI 操作系统代理。它不只是一个聊天机器人——它能直接操作你的文件系统、执行 Shell 命令、访问互联网，通过多智能体架构完成复杂任务。

**一句话：** 给它一句话，它帮你干活。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.3.5, Java 21 |
| LLM | DeepSeek / Qwen（兼容 OpenAI API 格式） |
| 前端 | 原生 HTML/CSS/JS（零框架依赖） |
| 通信 | WebSocket / SSE |
| 构建 | Maven 3.9 |

---

## 核心架构

### 多智能体系统

```
用户输入 → 🎯 调度员 → 意图分析 → 分配专家
                                 ├── 💻 代码专家（写代码、调试、架构）
                                 ├── 📝 文档专家（文档、翻译、报告）
                                 └── 🔧 系统专家（运维、调试、环境配置）
```

调度员使用 LLM 分析用户意图，自动选择最合适的专家 Agent。每个专家有独立的系统提示词和工具权限，保证职责分离和安全控制。

### 工具链

| 工具 | 功能 | 权限控制 |
|------|------|----------|
| `exec` | 执行 Shell 命令 | 危险命令黑名单 |
| `read_file` | 读取文件内容 | 沙箱路径限制 |
| `write_file` | 写入/创建文件 | 沙箱路径限制 |
| `list_dir` | 列出目录内容 | 沙箱路径限制 |
| `web_fetch` | 网页内容抓取 | 无 |

### 安全机制

- **沙箱隔离**：所有文件操作限制在 `sandbox/` 目录内，防止路径穿越
- **命令黑名单**：`rm -rf /`、`mkfs`、`dd if=` 等危险命令被拦截
- **超时控制**：命令执行超时 10 秒，防止长时间挂起
- **权限分级**：不同 Agent 拥有不同工具权限（文档专家不能执行 Shell）

### 记忆系统

- 每次对话自动持久化到 `sandbox/.memory/`
- 启动时自动加载历史会话（保留最近 20 条）
- 支持对话历史追溯和上下文恢复

---

## 快速开始

### 环境要求

- Java 21+
- Maven 3.8+
- DeepSeek 或 Qwen API Key

### 编译运行

```bash
cd aios
mvn clean package -DskipTests
java -jar target/aios-0.1.0.jar
```

启动后访问：`http://localhost:8080`

首次使用需在设置页配置 API Key 和模型。

---

## API 接口

### 聊天
- `POST /api/chat` - 发送消息，返回 AI 回复
- `GET /api/chat/stream` - SSE 流式聊天

### 配置
- `GET /api/config` - 获取当前配置
- `POST /api/config` - 保存配置

### 文件浏览器
- `GET /api/files?path=.` - 列出目录内容
- `GET /api/files/read?path=hello.txt` - 读取文件
- `POST /api/files/write` - 写入文件

### 沙箱管理
- `GET /api/sandbox/info` - 获取沙箱路径信息
- `POST /api/sandbox/open` - 在系统文件管理器中打开沙箱目录

### 记忆
- `GET /api/memory/files` - 列出记忆文件
- `GET /api/history` - 查看会话历史
- `DELETE /api/history` - 清空会话历史

---

## 项目结构

```
aios/
├── pom.xml
├── aios-config.json          # 运行时配置
├── sandbox/                  # 沙箱工作目录
│   └── .memory/             # 持久化记忆
├── src/main/
│   ├── java/com/xunfen/aios/
│   │   ├── AiosApplication.java
│   │   ├── controller/
│   │   │   └── ChatController.java
│   │   ├── llm/
│   │   │   └── LlmClient.java
│   │   ├── model/
│   │   │   ├── AgentProfile.java
│   │   │   ├── AgentStep.java
│   │   │   ├── Message.java
│   │   │   ├── ToolCall.java
│   │   │   └── ToolResult.java
│   │   ├── security/
│   │   │   └── SecurityChecker.java
│   │   ├── service/
│   │   │   ├── AgentDispatcher.java
│   │   │   ├── ConfigService.java
│   │   │   ├── MemoryService.java
│   │   │   └── MultiAgentService.java
│   │   └── tools/
│   │       ├── ExecTool.java
│   │       ├── ListDirTool.java
│   │       ├── ReadFileTool.java
│   │       ├── Tool.java
│   │       ├── ToolRegistry.java
│   │       └── WebFetchTool.java
│   └── resources/
│       ├── application.yml
│       └── static/
│           ├── index.html
│           └── setup.html
└── target/
```


---

## 许可证

MIT
