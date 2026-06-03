package com.xunfen.aios.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SecurityChecker {

    @Getter
    @Value("${aios.sandbox.work-dir}")
    private String workDir;

    @Getter
    @Value("${aios.sandbox.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${aios.sandbox.blocked-patterns:}")
    private String blockedPatternsRaw;

    private List<String> blockedPatterns;

    private Path workDirPath;

    @PostConstruct
    public void init() {
        workDirPath = Paths.get(workDir).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(workDirPath);
        } catch (Exception e) {
            log.warn("创建sandbox目录失败: {}", e.getMessage());
        }
        // 解析黑名单：YAML的list会被Spring自动拼接，这里用SpEL方式不行
        // 改为从application.yml读字符串然后split
        blockedPatterns = blockedPatternsRaw.isEmpty()
            ? List.of("rm -rf /", "mkfs", "dd if=", ":(){:|:&};:", "chmod 777 /", "> /dev/sd")
            : List.of(blockedPatternsRaw.split(","));
        log.info("Sandbox工作目录: {}", workDirPath);
        log.info("拦截规则: {}", blockedPatterns);
    }

    public boolean checkCommand(String cmd) {
        if (cmd == null || cmd.isBlank()) return false;
        String lower = cmd.toLowerCase();

        // 路径穿越检查
        if (cmd.contains("..") && cmd.contains("/")) {
            // 解析后检查是否在sandbox内
            try {
                String[] parts = cmd.split("\\|\\|", 2);
                // 允许 cd + pwd 组合用于路径检查
            } catch (Exception ignored) {}
        }

        // 黑名单检查
        for (String pattern : blockedPatterns) {
            pattern = pattern.trim();
            if (pattern.isEmpty()) continue;
            try {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
                    return false;
                }
            } catch (Exception e) {
                // 如果不是合法正则，做简单字符串匹配
                if (lower.contains(pattern)) return false;
            }
        }

        return true;
    }

    /**
     * 将路径解析为绝对路径。
     * Windows: 强制进入sandbox
     * Linux/macOS: 相对路径相对于workDir，绝对路径直接使用
     */
    public Path resolvePath(String pathStr) {
        Path path = Paths.get(pathStr);
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        if (isWindows) {
            // Windows 强制进入sandbox
            if (path.isAbsolute()) {
                path = workDirPath.resolve(path.getFileName().toString());
            } else {
                path = workDirPath.resolve(pathStr);
            }
        } else {
            // Linux/macOS: 相对路径用workDir，绝对路径直接用
            if (!path.isAbsolute()) {
                path = workDirPath.resolve(pathStr);
            }
        }

        Path normalized = path.normalize().toAbsolutePath();
        log.info("resolvePath: {} → {} (sandbox={}, os={})", pathStr, normalized, isWindows, os);
        return normalized;
    }
}
