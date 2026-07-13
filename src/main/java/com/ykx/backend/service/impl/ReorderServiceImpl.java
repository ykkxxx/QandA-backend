package com.ykx.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykx.backend.config.RagProperties;
import com.ykx.backend.config.VectorProperties;
import com.ykx.backend.service.ReorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百炼重排：支持 gte-rerank-v2（原生）与 qwen3-rerank（compatible-api）两种 HTTP 形态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReorderServiceImpl implements ReorderService {

    private final RagProperties ragProperties;
    private final VectorProperties vectorProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 重排序（Rerank）核心方法
     * 作用：把向量搜索出来的片段 → 调用阿里云重排模型打分 → 按相关性从高到低排序 → 返回最相关的TOP K
     * @param query 用户的问题
     * @param hits 向量库搜索出来的片段列表
     * @return 重排后的最终片段列表
     */
    @Override
    public List<Map<String, Object>> rerank(String query, List<Map<String, Object>> hits) {

        // ====================== 1. 读取重排配置 ======================
        RagProperties.Rerank cfg = ragProperties.getRerank() != null
                ? ragProperties.getRerank()
                : new RagProperties.Rerank();

        // 如果没开启重排，或者没有结果 → 直接返回前 finalTopK 条，不做重排
        if (!cfg.isEnabled() || hits == null || hits.isEmpty()) {
            return truncateCopy(hits, cfg.getFinalTopK());
        }

        // ====================== 2. 检查API密钥 ======================
        String apiKey = resolveApiKey(cfg);
        if (!StringUtils.hasText(apiKey)) {
            log.warn("重排未配置 api-key，跳过重排");
            return truncateCopy(hits, cfg.getFinalTopK());
        }

        // ====================== 3. 提取所有片段的文本内容 ======================
        // 把向量搜索出来的每一条的 content 拿出来，准备传给重排模型
        List<String> documents = new ArrayList<>(hits.size());
        for (Map<String, Object> h : hits) {
            Object c = h.get("content");
            documents.add(c == null ? "" : String.valueOf(c));
        }

        try {
            // ====================== 4. 构造请求，调用阿里云重排接口 ======================
            String url = cfg.getApiUrl() != null ? cfg.getApiUrl().trim() : "";
            String bodyJson;

            // 判断接口类型：兼容OpenAI格式 / 阿里云原生格式
            boolean compatible = url.contains("compatible-api/v1/reranks");
            if (compatible) {
                // 兼容通义千问3 OpenAI格式
                bodyJson = buildQwen3CompatibleBody(cfg, query, documents);
            } else {
                // 阿里云原生 GTE 重排格式
                bodyJson = buildGteNativeBody(cfg, query, documents);
            }

            // 请求头：JSON + 身份认证
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 发送POST请求
            HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            // ====================== 5. 处理接口返回 ======================
            String raw = response.getBody();
            if (raw == null || raw.isEmpty()) {
                log.warn("重排接口返回空，回退为向量顺序");
                return truncateCopy(hits, cfg.getFinalTopK());
            }

            // 把返回JSON转成Map
            Map<String, Object> root = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});

            // 如果接口返回错误码 → 跳过重排
            if (root.containsKey("code") && root.get("code") != null
                    && !String.valueOf(root.get("code")).isBlank()
                    && !"null".equalsIgnoreCase(String.valueOf(root.get("code")))) {
                log.warn("重排业务错误：{} {}", root.get("code"), root.get("message"));
                return truncateCopy(hits, cfg.getFinalTopK());
            }

            // ====================== 6. 解析重排结果 → 重新排序 ======================
            List<Map<String, Object>> ordered = compatible
                    ? parseQwen3CompatibleResults(root, hits, cfg)
                    : parseGteNativeResults(root, hits, cfg);

            // 返回最终排好序的列表（已过滤低分 + 只保留TOP K）
            return ordered;

        } catch (Exception e) {
            // 接口调用异常 → 回退到原始向量顺序，不做重排
            log.warn("重排调用失败，回退为向量顺序：{}", e.getMessage());
            int k = ragProperties.getRerank() != null ? ragProperties.getRerank().getFinalTopK() : 5;
            return truncateCopy(hits, k);
        }
    }

    private String resolveApiKey(RagProperties.Rerank cfg) {
        if (StringUtils.hasText(cfg.getApiKey())) {
            return cfg.getApiKey().trim();
        }
        if (vectorProperties.getEmbedding() != null
                && StringUtils.hasText(vectorProperties.getEmbedding().getApiKey())) {
            return vectorProperties.getEmbedding().getApiKey().trim();
        }
        return null;
    }

    private String buildGteNativeBody(RagProperties.Rerank cfg, String query, List<String> documents)
            throws JsonProcessingException {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("return_documents", Boolean.TRUE);
        parameters.put("top_n", documents.size());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("input", input);
        body.put("parameters", parameters);
        return objectMapper.writeValueAsString(body);
    }

    private String buildQwen3CompatibleBody(RagProperties.Rerank cfg, String query, List<String> documents)
            throws JsonProcessingException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", documents.size());
        return objectMapper.writeValueAsString(body);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseGteNativeResults(
            Map<String, Object> root,
            List<Map<String, Object>> hits,
            RagProperties.Rerank cfg) {

        Object outputObj = root.get("output");
        if (!(outputObj instanceof Map)) {
            return truncateCopy(hits, cfg.getFinalTopK());
        }
        Map<String, Object> output = (Map<String, Object>) outputObj;
        Object resultsObj = output.get("results");
        if (!(resultsObj instanceof List<?> resultList) || resultList.isEmpty()) {
            return truncateCopy(hits, cfg.getFinalTopK());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : resultList) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> r = (Map<String, Object>) item;
            double score = toDouble(r.get("relevance_score"));
            if (score < cfg.getMinRelevanceScore()) {
                continue;
            }
            int idx = toInt(r.get("index"));
            if (idx < 0 || idx >= hits.size()) {
                continue;
            }
            Map<String, Object> copy = new HashMap<>(hits.get(idx));
            copy.put("rerank_score", score);
            out.add(copy);
            if (out.size() >= cfg.getFinalTopK()) {
                break;
            }
        }
        return out.isEmpty() ? truncateCopy(hits, cfg.getFinalTopK()) : out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQwen3CompatibleResults(
            Map<String, Object> root,
            List<Map<String, Object>> hits,
            RagProperties.Rerank cfg) {

        Object resultsObj = root.get("results");
        if (!(resultsObj instanceof List<?> resultList) || resultList.isEmpty()) {
            return truncateCopy(hits, cfg.getFinalTopK());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : resultList) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> r = (Map<String, Object>) item;
            double score = toDouble(r.get("relevance_score"));
            if (score < cfg.getMinRelevanceScore()) {
                continue;
            }
            int idx = toInt(r.get("index"));
            if (idx < 0 || idx >= hits.size()) {
                continue;
            }
            Map<String, Object> copy = new HashMap<>(hits.get(idx));
            copy.put("rerank_score", score);
            out.add(copy);
            if (out.size() >= cfg.getFinalTopK()) {
                break;
            }
        }
        return out.isEmpty() ? truncateCopy(hits, cfg.getFinalTopK()) : out;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 安全截取前 finalTopK 条结果，并复制一份新列表返回
     * @param hits 原始的片段列表（向量召回或重排后的）
     * @param finalTopK 要保留几条（比如 5 条）
     * @return 截取后的新列表
     */
    private static List<Map<String, Object>> truncateCopy(List<Map<String, Object>> hits, int finalTopK) {

        // 如果原始列表是空的，直接返回空列表，不报错
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        // ==========================================
        // 核心：计算最终要截取几条
        // 1. 至少保留 1 条（Math.max(finalTopK, 1)）
        // 2. 不能超过原始列表的总条数
        // ==========================================
        int n = Math.min(Math.max(finalTopK, 1), hits.size());

        // 新建一个小列表，容量是 n
        List<Map<String, Object>> list = new ArrayList<>(n);

        // 循环把前 n 条 复制 进去
        for (int i = 0; i < n; i++) {
            list.add(new HashMap<>(hits.get(i))); // 重点：复制一份新Map！
        }

        // 返回截取后的新列表
        return list;
    }
}
