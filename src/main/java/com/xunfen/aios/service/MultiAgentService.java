package com.xunfen.aios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xunfen.aios.llm.LlmClient;
import com.xunfen.aios.model.AgentProfile;
import com.xunfen.aios.model.AgentStep;
import com.xunfen.aios.model.Message;
import com.xunfen.aios.model.ToolCall;
import com.xunfen.aios.model.ToolResult;
import com.xunfen.aios.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentService {

    private final LlmClient llm;
    private final ToolRegistry tools;
    private final AgentDispatcher dispatcher;

    private static final int MAX_ITERATIONS = 10;

    public ProcessResult process(String userInput, List<Message> history) {
        AgentDispatcher.DispatchResult dispatch = dispatcher.dispatch(userInput);
        log.info("调度结果: expert={}, reason={}",
            dispatch.isUseDispatcher() ? "dispatcher" : dispatch.getExpert().getId(),
            dispatch.getReason());

        AgentProfile agent = dispatch.isUseDispatcher() ? AgentProfile.dispatcher() : dispatch.getExpert();
        String systemPrompt = buildSystemPrompt(agent);

        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(systemPrompt));
        messages.addAll(history);
        messages.add(Message.user(userInput));

        List<AgentStep> steps = new ArrayList<>();
        steps.add(AgentStep.builder()
            .agentId("dispatcher")
            .agentName("调度员")
            .emoji("🎯")
            .type("dispatch")
            .content(dispatch.getReason() + " → " + (dispatch.isUseDispatcher() ? "调度员" : agent.getEmoji() + " " + agent.getName()))
            .build());

        StringBuilder finalAnswer = new StringBuilder();
        int iteration = 0;

        while (iteration < MAX_ITERATIONS) {
            iteration++;
            log.info("=== Agent[{}] 第{}轮 ===", agent.getName(), iteration);

            String response = llm.chat(messages);

            JsonNode result;
            try {
                result = new ObjectMapper().readTree(response);
            } catch (Exception e) {
                messages.add(Message.assistant(response));
                steps.add(AgentStep.builder()
                    .agentId(agent.getId()).agentName(agent.getName()).emoji(agent.getEmoji())
                    .type("answer").content(extractAnswer(response)).build());
                return ProcessResult.builder().response(extractAnswer(response)).agent(agent).iterations(iteration).steps(steps).build();
            }

            if (result.has("tool")) {
                String toolName = result.get("tool").asText();

                if (!agent.getTools().contains(toolName)) {
                    log.warn("Agent[{}] 无权使用工具: {}", agent.getName(), toolName);
                    messages.add(Message.assistant(response));
                    messages.add(Message.tool("拒绝: 你没有权限使用工具 '" + toolName + "'", "pd_" + iteration));
                    steps.add(AgentStep.builder()
                        .agentId(agent.getId()).agentName(agent.getName()).emoji(agent.getEmoji())
                        .type("error").content("无权使用工具: " + toolName).build());
                    continue;
                }

                Map<String, String> args = new HashMap<>();
                if (result.has("args")) {
                    JsonNode argsNode = result.get("args");
                    argsNode.fields().forEachRemaining(f -> args.put(f.getKey(), f.getValue().asText()));
                }
                ToolCall call = ToolCall.builder()
                    .id("call_" + iteration + "_" + System.currentTimeMillis())
                    .tool(toolName).args(args).build();

                messages.add(Message.assistantToolCalls(response, List.of(call)));

                steps.add(AgentStep.builder()
                    .agentId(agent.getId()).agentName(agent.getName()).emoji(agent.getEmoji())
                    .type("tool").content(toolName + ": " + argsToString(args)).build());

                ToolResult tr = tools.execute(call);
                log.info("执行工具: {} → exit={}", toolName, tr.getExitCode());

                messages.add(Message.tool(
                    String.format("[%s] exit=%d\n%s", tr.getTool(), tr.getExitCode(), tr.getOutput()),
                    call.getId()));

                if (result.has("answer")) {
                    String answer = result.get("answer").asText();
                    finalAnswer.append(answer);
                    steps.add(AgentStep.builder()
                        .agentId(agent.getId()).agentName(agent.getName()).emoji(agent.getEmoji())
                        .type("answer").content(answer).build());
                    break;
                }
            } else {
                String answerText = extractAnswer(response);
                messages.add(Message.assistant(response));
                steps.add(AgentStep.builder()
                    .agentId(agent.getId()).agentName(agent.getName()).emoji(agent.getEmoji())
                    .type("answer").content(answerText).build());
                return ProcessResult.builder().response(answerText).agent(agent).iterations(iteration).steps(steps).build();
            }
        }

        if (iteration >= MAX_ITERATIONS) {
            steps.add(AgentStep.builder()
                .agentId(agent.getId()).agentName(agent.getName()).emoji(agent.getEmoji())
                .type("error").content("达到最大迭代次数(" + MAX_ITERATIONS + ")").build());
            return ProcessResult.builder().response("达到最大迭代次数").agent(agent).iterations(iteration).steps(steps).build();
        }

        String finalText = finalAnswer.toString();
        if (finalText.isEmpty()) {
            for (int i = steps.size() - 1; i >= 0; i--) {
                AgentStep s = steps.get(i);
                if ("answer".equals(s.getType()) && !s.getContent().isEmpty()) {
                    finalText = s.getContent();
                    break;
                }
            }
            if (finalText.isEmpty()) {
                for (int i = steps.size() - 1; i >= 0; i--) {
                    AgentStep s = steps.get(i);
                    if ("tool".equals(s.getType()) && !s.getContent().isEmpty()) {
                        finalText = "操作完成: " + s.getContent();
                        break;
                    }
                }
            }
            if (finalText.isEmpty()) {
                finalText = "操作完成";
            }
        }
        return ProcessResult.builder().response(finalText).agent(agent).iterations(iteration).steps(steps).build();
    }

    /**
     * 从LLM返回的JSON中提取answer字段
     */
    private String extractAnswer(String response) {
        try {
            JsonNode node = new ObjectMapper().readTree(response);
            if (node.has("answer")) {
                return node.get("answer").asText();
            }
        } catch (Exception ignored) {}
        return response;
    }

    private String argsToString(Map<String, String> args) {
        if (args == null || args.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : args.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            String val = e.getValue().length() > 80 ? e.getValue().substring(0, 80) + "..." : e.getValue();
            sb.append(e.getKey()).append("=").append(val);
        }
        return sb.toString();
    }

    private String buildSystemPrompt(AgentProfile agent) {
        String availableTools = tools.getToolNames().stream()
            .filter(agent.getTools()::contains)
            .map(name -> {
                var tool = tools.getTool(name);
                return tool != null ? String.format("- %s: %s\n  参数: %s",
                    name, tool.description(), tool.parametersJson()) : "";
            })
            .filter(s -> !s.isEmpty())
            .reduce((a, b) -> a + "\n" + b).orElse("无可用工具");

        return """
            %s

            可用工具：
            %s

            你必须返回严格的JSON格式，支持两种格式：

            1. 执行工具调用：
            {"tool": "工具名", "args": {"参数名": "参数值"}}

            2. 直接回答（不调用工具）：
            {"answer": "你的回答文本"}

            规则：
            - 每次只能返回一个JSON对象
            - 文件路径可以是相对路径或绝对路径
            - 如果任务需要多步操作，先执行第一步，等结果再继续
            - 完成后用 answer 字段给出总结
            - 不要返回除JSON以外的任何内容
            """.formatted(agent.getSystemPrompt(), availableTools);
    }

    @lombok.Builder
    @lombok.Data
    public static class ProcessResult {
        private String response;
        private AgentProfile agent;
        private int iterations;
        private List<AgentStep> steps;
    }
}
