package com.xunfen.aios.tools;

import com.xunfen.aios.model.ToolCall;
import com.xunfen.aios.model.ToolResult;
import com.xunfen.aios.security.SecurityChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListDirTool implements Tool {

    private final SecurityChecker security;

    @Override
    public String name() { return "list_dir"; }

    @Override
    public String description() { return "列出目录内容"; }

    @Override
    public String parametersJson() {
        return """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "目录路径，默认当前工作目录"}
              },
              "required": []
            }""";
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String pathStr = call.getArgs().getOrDefault("path", ".");

        try {
            Path path = security.resolvePath(pathStr);
            if (!Files.isDirectory(path)) return ToolResult.fail(name(), "不是目录: " + pathStr);

            String listing = Files.list(path)
                .map(p -> {
                    try {
                        String type = Files.isDirectory(p) ? "[DIR] " : "[FILE]";
                        long size = Files.isRegularFile(p) ? Files.size(p) : 0;
                        return type + " " + p.getFileName() + (size > 0 ? " (" + size + "B)" : "");
                    } catch (Exception e) {
                        return "[ERR] " + p.getFileName();
                    }
                })
                .collect(Collectors.joining("\n"));

            if (listing.isBlank()) listing = "(空目录)";
            return ToolResult.ok(name(), listing);
        } catch (Exception e) {
            return ToolResult.fail(name(), "列出目录失败: " + e.getMessage());
        }
    }
}
