package com.xunfen.aios.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String role;       // system, user, assistant, tool
    private String content;
    private String toolCallId; // 仅 role=tool 时需要，用于关联工具调用
    private java.util.List<ToolCall> toolCalls; // 仅 role=assistant 时需要，用于声明工具调用

    public static Message system(String content) {
        return Message.builder().role("system").content(content).build();
    }
    public static Message user(String content) {
        return Message.builder().role("user").content(content).build();
    }
    public static Message assistant(String content) {
        return Message.builder().role("assistant").content(content).build();
    }
    public static Message assistantToolCalls(String content, java.util.List<ToolCall> toolCalls) {
        return Message.builder().role("assistant").content(content).toolCalls(toolCalls).build();
    }
    public static Message tool(String content, String toolCallId) {
        return Message.builder().role("tool").content(content).toolCallId(toolCallId).build();
    }
}
