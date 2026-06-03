package com.xunfen.aios.tools;

import com.xunfen.aios.model.ToolCall;
import com.xunfen.aios.model.ToolResult;
import com.xunfen.aios.security.SecurityChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecTool implements Tool {

    private final SecurityChecker security;

    @Override
    public String name() { return "exec"; }

    @Override
    public String description() { return "执行shell命令"; }

    @Override
    public String parametersJson() {
        return """
            {
              "type": "object",
              "properties": {
                "command": {"type": "string", "description": "要执行的shell命令"}
              },
              "required": ["command"]
            }""";
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String cmd = call.getArgs().get("command");
        if (cmd == null) return ToolResult.fail(name(), "缺少 command 参数");

        // 安全检查
        if (!security.checkCommand(cmd)) {
            log.warn("拒绝危险命令: {}", cmd);
            return ToolResult.fail(name(), "拒绝执行: 该命令被安全策略拦截");
        }

        try {
            String workDir = security.getWorkDir();
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
            pb.directory(new java.io.File(workDir));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(security.getTimeoutSeconds(), TimeUnit.SECONDS);

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (!finished) {
                process.destroyForcibly();
                return ToolResult.fail(name(), "命令执行超时 (" + security.getTimeoutSeconds() + "s)");
            }

            String out = output.toString();
            if (out.isBlank()) out = "(无输出)";
            int exitCode = process.exitValue();
            log.info("exec [{}]: exit={}", cmd, exitCode);
            return exitCode == 0 ? ToolResult.ok(name(), out) : ToolResult.fail(name(), out);
        } catch (Exception e) {
            return ToolResult.fail(name(), "执行异常: " + e.getMessage());
        }
    }
}
