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

    @Override
    public List<Map<String, Object>> rerank(String query, List<Map<String, Object>> hits) {
        RagProperties.Rerank cfg = ragProperties.getRerank() != null
                ? ragProperties.getRerank()
                : new RagProperties.Rerank();
        if (!cfg.isEnabled() || hits == null || hits.isEmpty()) {
            return truncateCopy(hits, cfg.getFinalTopK());
        }

        String apiKey = resolveApiKey(cfg);
        if (!StringUtils.hasText(apiKey)) {
            log.warn("重排未配置 api-key，跳过重排");
            return truncateCopy(hits, cfg.getFinalTopK());
        }

        List<String> documents = new ArrayList<>(hits.size());
        for (Map<String, Object> h : hits) {
            Object c = h.get("content");
            documents.add(c == null ? "" : String.valueOf(c));
        }

        try {
            String url = cfg.getApiUrl() != null ? cfg.getApiUrl().trim() : "";
            String bodyJson;
            boolean compatible = url.contains("compatible-api/v1/reranks");
            if (compatible) {
                bodyJson = buildQwen3CompatibleBody(cfg, query, documents);
            } else {
                bodyJson = buildGteNativeBody(cfg, query, documents);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            String raw = response.getBody();
            if (raw == null || raw.isEmpty()) {
                log.warn("重排接口返回空，回退为向量顺序");
                return truncateCopy(hits, cfg.getFinalTopK());
            }

            Map<String, Object> root = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            if (root.containsKey("code") && root.get("code") != null
                    && !String.valueOf(root.get("code")).isBlank()
                    && !"null".equalsIgnoreCase(String.valueOf(root.get("code")))) {
                log.warn("重排业务错误：{} {}", root.get("code"), root.get("message"));
                return truncateCopy(hits, cfg.getFinalTopK());
            }

            List<Map<String, Object>> ordered = compatible
                    ? parseQwen3CompatibleResults(root, hits, cfg)
                    : parseGteNativeResults(root, hits, cfg);
            return ordered;
        } catch (Exception e) {
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

    private static List<Map<String, Object>> truncateCopy(List<Map<String, Object>> hits, int finalTopK) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        int n = Math.min(Math.max(finalTopK, 1), hits.size());
        List<Map<String, Object>> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new HashMap<>(hits.get(i)));
        }
        return list;
    }
}
