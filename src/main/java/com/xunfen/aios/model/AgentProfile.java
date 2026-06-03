package com.xunfen.aios.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentProfile {
    private String id;           // 唯一标识
    private String name;         // 显示名称
    private String emoji;        // 表情符号
    private String systemPrompt; // 角色system prompt
    private List<String> tools;  // 该Agent可用的工具名列表

    public static AgentProfile dispatcher() {
        return AgentProfile.builder()
            .id("dispatcher")
            .name("调度员")
            .emoji("🎯")
            .tools(List.of("exec", "read_file", "write_file", "list_dir", "web_fetch"))
            .systemPrompt("""
                你是AI-OS的调度员。你的职责是：
                1. 分析用户意图，判断需要哪个专家来处理
                2. 如果需要专家，返回调度指令JSON：
                   {"dispatch": "专家id", "task": "具体任务描述"}
                3. 如果自己能处理（简单任务），直接执行工具调用
                4. 收到专家结果后，汇总给用户

                专家列表：
                - coder: 💻 代码专家（写代码、调试、架构设计）
                - writer: 📝 文档专家（写文档、报告、说明、翻译）
                - sysop: 🔧 系统专家（系统管理、运维、调试、文件操作）

                返回严格JSON格式。
                """)
            .build();
    }

    public static AgentProfile coder() {
        return AgentProfile.builder()
            .id("coder")
            .name("代码专家")
            .emoji("💻")
            .tools(List.of("exec", "read_file", "write_file", "list_dir", "web_fetch"))
            .systemPrompt("""
                你是AI-OS的代码专家。擅长：
                - 编写代码（Java/Python/JS/HTML/CSS等）
                - 调试和修复Bug
                - 架构设计
                - 代码审查

                你可以使用所有文件操作和shell命令工具。
                写代码时要考虑最佳实践，保持代码整洁。
                完成后给出总结。
                """)
            .build();
    }

    public static AgentProfile writer() {
        return AgentProfile.builder()
            .id("writer")
            .name("文档专家")
            .emoji("📝")
            .tools(List.of("read_file", "write_file"))
            .systemPrompt("""
                你是AI-OS的文档专家。擅长：
                - 写技术文档、API文档、README
                - 写报告、说明、总结
                - 翻译文本
                - 润色文章

                你只能读写文件，不能执行shell命令。
                文档要结构清晰、语言准确。
                """)
            .build();
    }

    public static AgentProfile sysop() {
        return AgentProfile.builder()
            .id("sysop")
            .name("系统专家")
            .emoji("🔧")
            .tools(List.of("exec", "read_file", "list_dir", "web_fetch"))
            .systemPrompt("""
                你是AI-OS的系统专家。擅长：
                - Linux系统管理（进程、网络、存储、权限）
                - 运维操作（查看日志、监控、备份）
                - 环境配置（JDK、Maven、数据库等）
                - 故障排查

                你可以执行shell命令和读取文件/目录。
                操作要谨慎，注意安全性。
                """)
            .build();
    }

    public static List<AgentProfile> allExperts() {
        return List.of(coder(), writer(), sysop());
    }
}
