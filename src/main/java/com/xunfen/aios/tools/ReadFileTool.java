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
public class ReadFileTool implements Tool {

    private final SecurityChecker security;

    @Override
    public String name() { return "read_file"; }

    @Override
    public String description() { return "读取文件内容"; }

    @Override
    public String parametersJson() {
        return """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "文件路径(相对或绝对路径)"}
              },
              "required": ["path"]
            }""";
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String pathStr = call.getArgs().get("path");
        if (pathStr == null) return ToolResult.fail(name(), "缺少 path 参数");

        try {
            Path path = security.resolvePath(pathStr);
            if (!Files.exists(path)) return ToolResult.fail(name(), "文件不存在: " + pathStr);
            if (!Files.isReadable(path)) return ToolResult.fail(name(), "文件不可读: " + pathStr);

            // 限制文件大小
            long size = Files.size(path);
            if (size > 1024 * 1024) return ToolResult.fail(name(), "文件过大(>1MB)");

            String content = Files.readString(path);
            return ToolResult.ok(name(), content);
        } catch (Exception e) {
            return ToolResult.fail(name(), "读取失败: " + e.getMessage());
        }
    }
}
