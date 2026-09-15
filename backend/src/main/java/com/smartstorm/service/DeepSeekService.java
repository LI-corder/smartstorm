package com.smartstorm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * DeepSeek（OpenAI 兼容 chat/completions）HTTP 客户端。
 *
 * <p>只负责"发消息→拿回结构化 JSON 文本并容错解析"，业务编排见 {@link AnalysisService}。</p>
 */
@Service
public class DeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekService.class);

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    public DeepSeekService(ObjectMapper objectMapper,
                           @Value("${app.deepseek.base-url}") String baseUrl,
                           @Value("${app.deepseek.api-key}") String apiKey,
                           @Value("${app.deepseek.model}") String model,
                           @Value("${app.deepseek.timeout-seconds}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout((int) timeoutSeconds * 1000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** 当前使用的模型 id（写入 board_analysis.model） */
    public String getModel() {
        return model;
    }

    /**
     * 发送一轮对话，要求模型返回 JSON，容错解析为 JsonNode。
     * 网络/服务端异常 → IllegalStateException（对用户友好的中文提示）。
     */
    public JsonNode chatJson(String systemPrompt, String userContent) {
        JsonNode root;
        try {
            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userContent);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.set("messages", messages);
            body.put("temperature", 0.2);
            body.put("stream", false);

            // 直接按 JsonNode 读回（Jackson 以 UTF-8 解码，避免中文变 mojibake）
            root = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    // 传对象由 Jackson 以 UTF-8 序列化，避免 String 转换器按 ISO-8859-1 破坏中文
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                throw new IllegalStateException("空响应");
            }
        } catch (Exception e) {
            log.error("DeepSeek 服务调用失败", e);
            throw new IllegalStateException("智能整理失败：DeepSeek 服务暂不可用，请稍后重试");
        }

        // 取出 assistant 消息的 content 文本
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (!contentNode.isTextual()) {
            log.error("DeepSeek 返回缺少文本内容: {}", root);
            throw new IllegalStateException("智能整理失败：AI 未返回文本内容，请重试");
        }
        String text = contentNode.asText();
        try {
            return parseJsonLenient(text);
        } catch (Exception e) {
            log.error("DeepSeek 返回内容无法解析: {}", text, e);
            throw new IllegalStateException("智能整理失败：AI 返回内容无法解析，请重试");
        }
    }

    /** 容忍 ```json 围栏 / 前后多余文字，截取首个 { 到最后一个 } */
    private JsonNode parseJsonLenient(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            if (nl >= 0) {
                text = text.substring(nl + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
        }
        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalStateException("返回内容中没有 JSON 对象");
        }
        try {
            return objectMapper.readTree(text.substring(first, last + 1));
        } catch (Exception e) {
            throw new IllegalStateException("JSON 语法错误: " + e.getMessage(), e);
        }
    }
}
