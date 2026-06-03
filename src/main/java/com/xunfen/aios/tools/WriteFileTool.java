package com.xunfen.aios.tools;

import com.xunfen.aios.model.ToolCall;
import com.xunfen.aios.model.ToolResult;
import com.xunfen.aios.security.SecurityChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class WriteFileTool implements Tool {

    private final SecurityChecker security;

    @Override
    public String name() { return "write_file"; }

    @Override
    public String description() { return "写入文件内容(文件不存在则创建)"; }

    @Override
    public String parametersJson() {
        return """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "文件路径"},
                "content": {"type": "string", "description": "文件内容"}
              },
              "required": ["path", "content"]
            }""";
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String pathStr = call.getArgs().get("path");
        String content = call.getArgs().get("content");
        if (pathStr == null) return ToolResult.fail(name(), "缺少 path 参数");
        if (content == null) content = "";

        try {
            Path path = security.resolvePath(pathStr);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            log.info("write_file: {}", pathStr);
            return ToolResult.ok(name(), "已写入: " + pathStr + " (" + content.length() + " 字节)");
        } catch (Exception e) {
            return ToolResult.fail(name(), "写入失败: " + e.getMessage());
        }
    }
}
