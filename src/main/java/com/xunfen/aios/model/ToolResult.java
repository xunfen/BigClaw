package com.xunfen.aios.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private String tool;
    private int exitCode;
    private String output;
    private boolean success;

    public static ToolResult ok(String tool, String output) {
        return ToolResult.builder().tool(tool).exitCode(0).output(output).success(true).build();
    }

    public static ToolResult fail(String tool, String output) {
        return ToolResult.builder().tool(tool).exitCode(1).output(output).success(false).build();
    }
}
