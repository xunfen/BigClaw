package com.xunfen.aios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class ConfigService {

    private final Path configFile = Path.of("aios-config.json");
    private final ObjectMapper mapper = new ObjectMapper();

    @Data
    public static class AiosConfig {
        private String provider;   // deepseek | qwen
        private String model;
        private String apiKey;
        private boolean setupComplete;
    }

    private AiosConfig config;

    @PostConstruct
    public void init() {
        if (Files.exists(configFile)) {
            try {
                config = mapper.readValue(configFile.toFile(), AiosConfig.class);
                log.info("已加载配置: provider={}, model={}, setup={}", config.getProvider(), config.getModel(), config.isSetupComplete());
            } catch (Exception e) {
                log.warn("加载配置文件失败，使用默认值", e);
                config = defaultConfig();
            }
        } else {
            config = defaultConfig();
            log.info("未找到配置文件，使用默认值");
        }
    }

    private AiosConfig defaultConfig() {
        AiosConfig c = new AiosConfig();
        c.setSetupComplete(false);
        return c;
    }

    public AiosConfig getConfig() {
        return config;
    }

    public boolean isSetupComplete() {
        return config.isSetupComplete() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    public AiosConfig saveConfig(AiosConfig newConfig) {
        this.config = newConfig;
        newConfig.setSetupComplete(true);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), newConfig);
            log.info("配置已保存: {}", configFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("保存配置失败", e);
        }
        return this.config;
    }
}
