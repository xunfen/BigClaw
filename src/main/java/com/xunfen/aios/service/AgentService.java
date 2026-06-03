package com.xunfen.aios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xunfen.aios.llm.LlmClient;
import com.xunfen.aios.model.Message;
import com.xunfen.aios.model.ToolCall;
import com.xunfen.aios.model.ToolResult;
import com.xunfen.aios.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final LlmClient llm;
    private final ToolRegistry tools;

    private static final int MAX_ITERATIONS = 10;  // 防止无限循环

    public String process(String userInput, List<Message> history) {
        String systemPrompt = buildSystemPrompt();

        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(systemPrompt));
        messages.addAll(history);
        messages.add(Message.user(userInput));

        StringBuilder finalAnswer = new StringBuilder();
        int iteration = 0;

        while (iteration < MAX_ITERATIONS) {
            iteration++;
            log.info("=== LLM 第 {} 轮调用 ===", iteration);

            // 调用 LLM
            String response = llm.chat(messages);

            // 解析 JSON
            JsonNode result;
            try {
                result = new ObjectMapper().readTree(response);
            } catch (Exception e) {
                log.warn("LLM返回非JSON: {}", response);
                return "AI: " + response;
            }

            // 检查是否有 tool 调用
            if (result.has("tool")) {
                // 解析工具调用
                List<ToolCall> calls = parseToolCalls(result);
                if (calls.isEmpty()) {
                    finalAnswer.append("AI: ").append(response);
                    break;
                }

                // 为每个调用生成独立ID
                for (ToolCall call : calls) {
                    call.setId("call_" + iteration + "_" + System.currentTimeMillis());
                }

                // 先添加带 tool_calls 的 assistant 消息
                messages.add(Message.assistantToolCalls(response, calls));

                // 执行工具
                StringBuilder toolResults = new StringBuilder();
                for (ToolCall call : calls) {
                    log.info("执行工具: {} ({})", call.getTool(), call.getArgs());
                    ToolResult tr = tools.execute(call);
                    toolResults.append(String.format("[%s] exit=%d success=%s\n%s\n",
                        tr.getTool(), tr.getExitCode(), tr.isSuccess(), tr.getOutput()));
                    // 每个 tool result 对应一个 tool_call_id
                    messages.add(Message.tool(
                        String.format("[%s] exit=%d\n%s", tr.getTool(), tr.getExitCode(), tr.getOutput()),
                        call.getId()
                    ));
                }

                // 不再把合并的 toolResults 单独添加

                // 如果AI返回了 final_answer，说明它认为任务完成了
                if (result.has("final_answer")) {
                    finalAnswer.append(result.get("final_answer").asText());
                    break;
                }
                // 否则继续下一轮
            } else {
                // 没有工具调用，直接返回
                messages.add(Message.assistant(response));
                return response;
            }
        }

        if (iteration >= MAX_ITERATIONS) {
            return "达到最大迭代次数(" + MAX_ITERATIONS + ")，任务可能未完成。";
        }

        return finalAnswer.toString();
    }

    private List<ToolCall> parseToolCalls(JsonNode node) {
        List<ToolCall> calls = new ArrayList<>();

        // 单个工具调用
        if (node.has("tool")) {
            String toolName = node.get("tool").asText();
            Map<String, String> args = new java.util.HashMap<>();
            if (node.has("args")) {
                JsonNode argsNode = node.get("args");
                argsNode.fields().forEachRemaining(f -> args.put(f.getKey(), f.getValue().asText()));
            }
            calls.add(ToolCall.builder().tool(toolName).args(args).build());
        }

        return calls;
    }

    private String buildSystemPrompt() {
        return """
            你是一个能直接操作Linux系统的AI助手。

            可用工具：
            %s

            你必须返回严格的JSON格式，支持以下两种格式：

            1. 执行工具调用：
            {"tool": "工具名", "args": {"参数名": "参数值"}}

            2. 直接回答（不调用工具）：
            {"answer": "你的回答文本"}

            规则：
            - 每次只能返回一个JSON对象
            - 文件路径可以是相对路径(相对于工作目录)或绝对路径
            - 如果任务需要多步操作，先执行第一步，等结果再继续
            - 完成后用 answer 字段给出总结
            - 不要返回除JSON以外的任何内容

            示例 - 创建文件并写入：
            {"tool": "write_file", "args": {"path": "hello.txt", "content": "Hello World"}}

            示例 - 查看目录：
            {"tool": "list_dir", "args": {"path": "."}}

            示例 - 执行命令：
            {"tool": "exec", "args": {"command": "echo hello"}}

            示例 - 直接回答：
            {"answer": "任务已完成，我创建了一个hello.txt文件"}
            """.formatted(tools.getToolsDescription());
    }
}
