package com.xunfen.aios.tools;

import com.xunfen.aios.model.ToolCall;
import com.xunfen.aios.model.ToolResult;

public interface Tool {
    String name();
    String description();
    String parametersJson();  // JSON Schema for LLM
    ToolResult execute(ToolCall call);
}
