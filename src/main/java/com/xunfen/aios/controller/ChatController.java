package com.xunfen.aios.controller;

import com.xunfen.aios.model.Message;
import com.xunfen.aios.service.AgentDispatcher;
import com.xunfen.aios.service.ConfigService;
import com.xunfen.aios.service.MemoryService;
import com.xunfen.aios.service.MultiAgentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final MultiAgentService multiAgentService;
    private final AgentDispatcher agentDispatcher;
    private final ConfigService configService;
    private final MemoryService memoryService;

    // 会话存储（内存级）
    private final List<Message> conversationHistory = new ArrayList<>();

    @jakarta.annotation.PostConstruct
    public void loadMemory() {
        // 启动时加载所有历史记忆
        List<Message> history = memoryService.loadAllHistory();
        // 只保留最近20条，避免上下文过长
        int start = Math.max(0, history.size() - 20);
        conversationHistory.addAll(history.subList(start, history.size()));
        log.info("恢复历史会话: {} 条", conversationHistory.size());
    }

    /**
     * 普通聊天（等待完成再返回）
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        if (!configService.isSetupComplete()) {
            return Map.of("error", true, "message", "请先完成模型配置");
        }
        String message = request.getOrDefault("message", "");
        log.info("用户输入: {}", message);

        MultiAgentService.ProcessResult result = multiAgentService.process(message, conversationHistory);
        conversationHistory.add(Message.user(message));
        conversationHistory.add(Message.assistant(result.getResponse()));

        // 保存到memory（异步不阻塞）
        CompletableFuture.runAsync(() -> memoryService.saveMessages(List.of(
            Message.user(message),
            Message.assistant(result.getResponse())
        )));

        // 尝试从JSON响应中提取answer字段
        String displayResponse = result.getResponse();
        try {
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(displayResponse);
            if (json.has("answer")) {
                displayResponse = json.get("answer").asText();
            }
        } catch (Exception ignored) {}

        return Map.of(
            "response", displayResponse,
            "agent", result.getAgent().getEmoji() + " " + result.getAgent().getName(),
            "agentId", result.getAgent().getId(),
            "iterations", result.getIterations(),
            "steps", result.getSteps()
        );
    }

    /**
     * SSE 流式聊天
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String message) {
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        CompletableFuture.runAsync(() -> {
            try {
                // 发送状态
                emitter.send(SseEmitter.event().name("status").data("thinking"));

                conversationHistory.add(Message.user(message));

                // 实际处理
                MultiAgentService.ProcessResult result = multiAgentService.process(message, conversationHistory);

                conversationHistory.add(Message.assistant(result.getResponse()));

                emitter.send(SseEmitter.event().name("status").data("done"));
                emitter.send(SseEmitter.event().name("response").data(Map.of(
                    "response", result.getResponse(),
                    "agent", result.getAgent().getEmoji() + " " + result.getAgent().getName(),
                    "agentId", result.getAgent().getId(),
                    "iterations", result.getIterations()
                )));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("处理失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("处理失败: " + e.getMessage()));
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * 检查配置状态
     */
    @GetMapping("/config")
    public ConfigService.AiosConfig getConfig() {
        return configService.getConfig();
    }

    /**
     * 保存配置
     */
    @PostMapping("/config")
    public ConfigService.AiosConfig saveConfig(@RequestBody ConfigService.AiosConfig cfg) {
        return configService.saveConfig(cfg);
    }

    /**
     * 查看会话历史
     */
    @GetMapping("/history")
    public List<Message> getHistory() {
        return List.copyOf(conversationHistory);
    }

    /**
     * 清空会话历史
     */
    @DeleteMapping("/history")
    public Map<String, String> clearHistory() {
        conversationHistory.clear();
        return Map.of("status", "cleared");
    }

    // ==================== 文件浏览器 API ====================

    /**
     * 列出sandbox目录内容
     */
    @GetMapping("/files")
    public Map<String, Object> listFiles(@RequestParam(defaultValue = ".") String path) {
        try {
            Path sandboxPath = Path.of("sandbox").resolve(path).normalize().toAbsolutePath();
            // 安全检查：必须在sandbox内
            Path sandboxRoot = Path.of("sandbox").toAbsolutePath().normalize();
            if (!sandboxPath.startsWith(sandboxRoot)) {
                return Map.of("error", "路径越界");
            }
            if (!Files.exists(sandboxPath)) {
                return Map.of("error", "路径不存在");
            }

            List<Map<String, Object>> items = new ArrayList<>();
            if (Files.isDirectory(sandboxPath)) {
                try (java.util.stream.Stream<Path> stream = Files.list(sandboxPath)) {
                    var sorted = stream.sorted(Comparator.<Path, Integer>comparing(p -> Files.isDirectory(p) ? 0 : 1)
                        .thenComparing(p -> p.getFileName().toString())).toList();
                    for (Path p : sorted) {
                        Map<String, Object> item = new java.util.LinkedHashMap<>();
                        item.put("name", p.getFileName().toString());
                        item.put("isDir", Files.isDirectory(p));
                        try { item.put("size", Files.size(p)); } catch (IOException ignored) { item.put("size", 0); }
                        items.add(item);
                    }
                }
            }
            return Map.of(
                "path", path.equals(".") ? "/" : path,
                "isDir", true,
                "items", items
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 读取文件内容
     */
    @GetMapping("/files/read")
    public Map<String, String> readFile(@RequestParam String path) {
        try {
            Path filePath = Path.of("sandbox").resolve(path).normalize().toAbsolutePath();
            Path sandboxRoot = Path.of("sandbox").toAbsolutePath().normalize();
            if (!filePath.startsWith(sandboxRoot)) {
                return Map.of("error", "路径越界");
            }
            if (!Files.exists(filePath)) {
                return Map.of("error", "文件不存在");
            }
            if (!Files.isReadable(filePath)) {
                return Map.of("error", "文件不可读");
            }
            long size = Files.size(filePath);
            if (size > 512 * 1024) {
                return Map.of("error", "文件过大(>512KB)");
            }
            return Map.of(
                "content", Files.readString(filePath),
                "path", path,
                "size", String.valueOf(size)
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 写入文件
     */
    @PostMapping("/files/write")
    public Map<String, String> writeFile(@RequestBody Map<String, String> request) {
        try {
            String path = request.get("path");
            String content = request.getOrDefault("content", "");
            if (path == null) return Map.of("error", "缺少path参数");

            Path filePath = Path.of("sandbox").resolve(path).normalize().toAbsolutePath();
            Path sandboxRoot = Path.of("sandbox").toAbsolutePath().normalize();
            if (!filePath.startsWith(sandboxRoot)) {
                return Map.of("error", "路径越界");
            }
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            return Map.of("status", "ok", "path", path, "size", String.valueOf(content.length()));
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    // ==================== 记忆管理 API ====================

    /**
     * 列出所有memory文件
     */
    @GetMapping("/memory/files")
    public Map<String, Object> listMemoryFiles() {
        return Map.of("files", memoryService.listMemoryFiles());
    }

    // ==================== 沙箱快捷打开 ====================

    /**
     * 获取沙箱目录绝对路径（供前端在系统文件管理器中打开）
     */
    @GetMapping("/sandbox/info")
    public Map<String, String> getSandboxInfo() {
        Path sandboxRoot = Path.of("sandbox").toAbsolutePath().normalize();
        String os = System.getProperty("os.name").toLowerCase();
        return Map.of(
            "path", sandboxRoot.toString(),
            "os", os
        );
    }

    /**
     * 在系统文件管理器中打开沙箱目录
     */
    @PostMapping("/sandbox/open")
    public Map<String, String> openSandboxFolder() {
        try {
            Path sandboxRoot = Path.of("sandbox").toAbsolutePath().normalize();
            java.io.File folder = sandboxRoot.toFile();
            if (!folder.exists()) {
                return Map.of("error", "沙箱目录不存在");
            }

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", folder.getAbsolutePath());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", folder.getAbsolutePath());
            } else {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(folder);
                    return Map.of("status", "ok", "message", "已打开: " + folder.getAbsolutePath());
                } else {
                    pb = new ProcessBuilder("xdg-open", folder.getAbsolutePath());
                }
            }
            pb.start();
            return Map.of("status", "ok", "message", "已打开: " + folder.getAbsolutePath());
        } catch (Exception e) {
            return Map.of("error", "打开失败: " + e.getMessage());
        }
    }
}
