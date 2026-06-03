package com.xunfen.aios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xunfen.aios.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class MemoryService {

    @Value("${aios.sandbox.work-dir:./sandbox}")
    private String workDir;

    private Path memoryDir;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

    @PostConstruct
    public void init() {
        memoryDir = Path.of(workDir).resolve(".memory");
        try { Files.createDirectories(memoryDir); } catch (IOException ignored) {}
        log.info("Memory目录: {}", memoryDir.toAbsolutePath());
    }

    /**
     * 保存消息到今天的memory文件
     */
    public void saveMessages(List<Message> messages) {
        if (messages.isEmpty()) return;
        Path file = memoryDir.resolve(today + ".jsonl");
        try {
            StringBuilder sb = new StringBuilder();
            for (Message m : messages) {
                sb.append(mapper.writeValueAsString(m)).append("\n");
            }
            Files.writeString(file, sb.toString(), java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("保存memory失败: {}", e.getMessage());
        }
    }

    /**
     * 加载所有历史memory（按日期排序）
     */
    public List<Message> loadAllHistory() {
        List<Message> all = new ArrayList<>();
        try (Stream<Path> stream = Files.list(memoryDir)) {
            List<Path> files = stream
                .filter(p -> p.toString().endsWith(".jsonl"))
                .sorted()
                .toList();

            for (Path file : files) {
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (String line : lines) {
                        if (line.isBlank()) continue;
                        all.add(mapper.readValue(line, Message.class));
                    }
                } catch (IOException e) {
                    log.warn("加载memory文件失败 {}: {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("扫描memory目录失败: {}", e.getMessage());
        }
        log.info("加载了 {} 条历史消息", all.size());
        return all;
    }

    /**
     * 加载今天的消息
     */
    public List<Message> loadToday() {
        Path file = memoryDir.resolve(today + ".jsonl");
        if (!Files.exists(file)) return new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                if (line.isBlank()) continue;
                messages.add(mapper.readValue(line, Message.class));
            }
        } catch (IOException e) {
            log.warn("加载今日memory失败: {}", e.getMessage());
        }
        return messages;
    }

    /**
     * 列出所有memory文件
     */
    public List<String> listMemoryFiles() {
        try (Stream<Path> stream = Files.list(memoryDir)) {
            return stream
                .filter(p -> p.toString().endsWith(".jsonl"))
                .sorted(Comparator.reverseOrder())
                .map(p -> p.getFileName().toString())
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
