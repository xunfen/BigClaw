package com.xunfen.aios.tools;

import com.xunfen.aios.model.ToolCall;
import com.xunfen.aios.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
public class WebFetchTool implements Tool {

    private final WebClient webClient = WebClient.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
        .build();

    @Override
    public String name() { return "web_fetch"; }

    @Override
    public String description() { return "获取网页内容(自动提取正文，返回markdown格式)"; }

    @Override
    public String parametersJson() {
        return """
            {
              "type": "object",
              "properties": {
                "url": {"type": "string", "description": "要抓取的URL"}
              },
              "required": ["url"]
            }""";
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String url = call.getArgs().get("url");
        if (url == null) return ToolResult.fail(name(), "缺少 url 参数");

        // 安全检查：只允许 http/https
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.fail(name(), "只支持 http/https 协议");
        }

        try {
            String html = webClient.get()
                .uri(url)
                .header("User-Agent", "AI-OS/1.0 (AI-OS Web Fetcher)")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                .block();

            if (html == null) return ToolResult.fail(name(), "网页返回空内容");

            // 简单HTML转Markdown：提取text content
            String text = htmlToText(html);

            // 限制长度
            if (text.length() > 8000) {
                text = text.substring(0, 8000) + "\n\n...(内容已截断，共" + text.length() + "字)";
            }

            log.info("web_fetch: {} ({} chars)", url, text.length());
            return ToolResult.ok(name(), text);
        } catch (Exception e) {
            return ToolResult.fail(name(), "抓取失败: " + e.getMessage());
        }
    }

    /**
     * 简单的HTML转文本：去掉标签，保留基本结构
     */
    private String htmlToText(String html) {
        // 去掉script和style
        String text = html.replaceAll("(?is)<(script|style|noscript|header|footer|nav)[^>]*>.*?</\\1>", "");
        // 去掉所有HTML标签
        text = text.replaceAll("<[^>]+>", " ");
        // 解码常见HTML实体
        text = text.replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'");
        // 压缩空白
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.replaceAll("[ \\t]+", " ");
        // 去掉每行首尾空白
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
