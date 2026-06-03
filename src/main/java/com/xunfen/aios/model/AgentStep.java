package com.xunfen.aios.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentStep {
    private String agentId;     // 哪个agent执行的
    private String agentName;   // 显示名称
    private String emoji;       // 表情
    private String type;        // "dispatch" | "tool" | "answer"
    private String content;     // 内容
}
