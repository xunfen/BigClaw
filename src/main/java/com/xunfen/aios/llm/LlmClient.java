package com.xunfen.aios.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xunfen.aios.model.Message;
import com.xunfen.aios.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final ConfigService configService;
    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    public String chat(List<Message> messages) {
        var cfg = configService.getConfig();
        String baseUrl = "deepseek".equals(cfg.getProvider())
            ? "https://api.deepseek.com"
            : "https://dashscope.aliyuncs.com/compatible-mode/v1";

        try {
            // 用 LinkedHashMap 保证key顺序
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", cfg.getModel());
            body.put("messages", messages.stream().map(m -> {
                Map<String, Object> mm = new java.util.LinkedHashMap<>();
                mm.put("role", m.getRole());
                mm.put("content", m.getContent());
                if (m.getToolCallId() != null) {
                    mm.put("tool_call_id", m.getToolCallId());
                }
                // assistant 带 tool_calls
                if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                    java.util.List<Map<String, Object>> toolCallsList = new java.util.ArrayList<>();
                    for (var tc : m.getToolCalls()) {
                        Map<String, Object> tcObj = new java.util.LinkedHashMap<>();
                        tcObj.put("id", tc.getId());
                        tcObj.put("type", "function");
                        Map<String, Object> funcObj = new java.util.LinkedHashMap<>();
                        funcObj.put("name", tc.getTool());
                        funcObj.put("arguments", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(tc.getArgs()).toString());
                        tcObj.put("function", funcObj);
                        toolCallsList.add(tcObj);
                    }
                    mm.put("tool_calls", toolCallsList);
                }
                return mm;
            }).toList());
            body.put("temperature", 0.7);
            body.put("response_format", Map.of("type", "json_object"));

            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            log.info("请求URL: {}, 模型: {}", url, cfg.getModel());

            String response = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse ->
                    clientResponse.bodyToMono(String.class).map(err -> {
                        log.error("LLM错误响应: {}", err);
                        return new RuntimeException("API错误: " + err);
                    })
                )
                .bodyToMono(String.class)
                .block();

            JsonNode root = mapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            log.info("LLM响应 ({} chars)", content.length());
            return content;
        } catch (Exception e) {
            log.error("LLM调用失败: {}", e.getMessage());
            return "{\"answer\": \"LLM调用失败: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
