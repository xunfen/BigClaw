package com.xunfen.aios.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.xunfen.aios.model.ToolCall;
import com.xunfen.aios.model.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<Tool> tools;

    /**
     * 生成工具描述JSON，用于LLM system prompt
     */
    public String getToolsDescription() {
        return tools.stream()
            .map(t -> String.format("- %s: %s\n  参数: %s", t.name(), t.description(), t.parametersJson()))
            .collect(Collectors.joining("\n"));
    }

    public Tool getTool(String name) {
        return tools.stream()
            .filter(t -> t.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    public ToolResult execute(ToolCall call) {
        Tool tool = getTool(call.getTool());
        if (tool == null) {
            return ToolResult.fail(call.getTool(), "未知工具: " + call.getTool());
        }
        try {
            return tool.execute(call);
        } catch (Exception e) {
            return ToolResult.fail(call.getTool(), "执行异常: " + e.getMessage());
        }
    }

    public List<String> getToolNames() {
        return tools.stream().map(Tool::name).toList();
    }
}
