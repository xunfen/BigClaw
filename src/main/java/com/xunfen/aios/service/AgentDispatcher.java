package com.xunfen.aios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xunfen.aios.llm.LlmClient;
import com.xunfen.aios.model.AgentProfile;
import com.xunfen.aios.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDispatcher {

    private final LlmClient llm;

    /**
     * 分析用户意图，返回应该使用的专家Agent
     */
    public DispatchResult dispatch(String userInput) {
        String expertList = AgentProfile.allExperts().stream()
            .map(a -> String.format("- %s(%s): %s", a.getId(), a.getName(),
                a.getSystemPrompt().split("\n")[2].trim()))
            .reduce((a, b) -> a + "\n" + b).orElse("");

        String systemPrompt = """
            你是一个任务调度分析器。分析用户请求，判断最适合处理的专家。

            专家列表：
            %s

            返回严格JSON：
            {"expert": "专家id", "reason": "选择原因(简短)"}

            如果请求很简单（如"你好"、"谢谢"），expert设为"none"。
            如果不确定，选最接近的专家。
            """.formatted(expertList);

        List<Message> messages = List.of(
            Message.system(systemPrompt),
            Message.user(userInput)
        );

        try {
            String response = llm.chat(messages);
            JsonNode result = new ObjectMapper().readTree(response);

            if (result.has("expert")) {
                String expertId = result.get("expert").asText();
                String reason = result.has("reason") ? result.get("reason").asText() : "";

                if ("none".equals(expertId)) {
                    return DispatchResult.builder()
                        .useDispatcher(true)
                        .reason("简单请求，调度员直接处理")
                        .build();
                }

                // 查找匹配的专家
                AgentProfile expert = AgentProfile.allExperts().stream()
                    .filter(a -> a.getId().equals(expertId))
                    .findFirst()
                    .orElse(null);

                if (expert != null) {
                    return DispatchResult.builder()
                        .useDispatcher(false)
                        .expert(expert)
                        .reason(reason)
                        .build();
                }
            }
        } catch (Exception e) {
            log.warn("调度分析失败: {}", e.getMessage());
        }

        // 默认返回调度员
        return DispatchResult.builder()
            .useDispatcher(true)
            .reason("调度失败，使用默认处理")
            .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class DispatchResult {
        private boolean useDispatcher;  // true=调度员处理, false=专家处理
        private AgentProfile expert;     // 专家profile
        private String reason;           // 调度原因
    }
}
