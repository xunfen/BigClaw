package com.xunfen.aios.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String id;    // tool_call_id, 如 "call_1_123456"
    private String tool;  // 工具名
    private java.util.Map<String, String> args;
}
