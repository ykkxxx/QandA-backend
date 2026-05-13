package com.ykx.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykx.backend.config.VectorProperties;
import com.ykx.backend.service.VectorService;
import com.ykx.backend.vector.adapter.MilvusClientAdapter;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.InsertParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorServiceImpl implements VectorService {

    private final MilvusClientAdapter milvusClientAdapter;
    private final VectorProperties vectorProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<Float> createEmbedding(String text) {
        try {
            String url = vectorProperties.getEmbedding().getApiUrl();
            String key = vectorProperties.getEmbedding().getApiKey();
            if (!StringUtils.hasText(url)) {
                throw new RuntimeException("未配置嵌入接口地址 vector.embedding.api-url");
            }
            if (!StringUtils.hasText(key)) {
                throw new RuntimeException(
                        "未配置嵌入 API Key：使用百炼兼容接口时，请设置环境变量 VECTOR_EMBEDDING_API_KEY，或在 application 中配置 vector.embedding.api-key。");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);

            String trimmedUrl = url.trim();
            boolean compatibleOpenAi = trimmedUrl.contains("compatible-mode");

            Map<String, Object> param = new LinkedHashMap<>();
            param.put("model", vectorProperties.getEmbedding().getModel());
            Integer dim = vectorProperties.getMilvus() != null ? vectorProperties.getMilvus().getDimension() : null;

            if (compatibleOpenAi) {
                // OpenAI 兼容：/compatible-mode/v1/embeddings
                param.put("input", text);
                if (dim != null) {
                    param.put("dimensions", dim);
                }
            } else {
                // 百炼原生：.../services/embeddings/text-embedding/text-embedding
                Map<String, Object> inputObj = new LinkedHashMap<>();
                inputObj.put("texts", List.of(text));
                param.put("input", inputObj);
                if (dim != null) {
                    Map<String, Object> parameters = new LinkedHashMap<>();
                    parameters.put("dimension", dim);
                    param.put("parameters", parameters);
                }
            }

            String jsonBody = objectMapper.writeValueAsString(param);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    trimmedUrl,
                    HttpMethod.POST,
                    entity,
                    String.class);

            String raw = response.getBody();
            if (raw == null || raw.isEmpty()) {
                throw new RuntimeException("嵌入接口返回空响应");
            }
            Map<String, Object> body = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            return parseEmbeddingResponse(body, compatibleOpenAi);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("嵌入请求 JSON 序列化/解析失败：" + e.getOriginalMessage(), e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                throw new RuntimeException(
                        "嵌入服务返回 401：密钥无效或未授权。若使用阿里云百炼兼容地址，请使用 DashScope 控制台申请的 API Key（不是 DeepSeek 的 sk），并配置 VECTOR_EMBEDDING_API_KEY 或 vector.embedding.api-key。",
                        e);
            }
            int code = e.getStatusCode().value();
            String bodySnippet = e.getResponseBodyAsString();
            if (StringUtils.hasText(bodySnippet) && bodySnippet.length() > 500) {
                bodySnippet = bodySnippet.substring(0, 500) + "...";
            }
            String hint = code == 404
                    ? "（若请求的是 DeepSeek 的 /v1/embeddings，官方当前普遍返回 404，嵌入需换用百炼等提供该路由的服务。）"
                    : "";
            String detail = StringUtils.hasText(bodySnippet) ? " 响应：" + bodySnippet : "";
            throw new RuntimeException("嵌入服务 HTTP " + code + "：" + e.getStatusText() + hint + detail, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("生成向量失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<String> splitText(String text) {
        int size = vectorProperties.getChunk().getSize();
        int overlap = vectorProperties.getChunk().getOverlap();
        int step = size - overlap;
        if (step <= 0) {
            step = size;
        }

        List<String> list = new ArrayList<>();
        int len = text.length();
        for (int i = 0; i < len; i += step) {
            int end = Math.min(i + size, len);
            list.add(text.substring(i, end));
        }
        return list;
    }

    @Override
    public void saveToMilvus(String userId, String content, String sourceFile) {
        List<String> chunks = splitText(content);
        int index = 0;
        for (String chunk : chunks) {
            List<Float> vector = createEmbedding(chunk);
            String id = UUID.randomUUID().toString();

            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field("id", Collections.singletonList(id)),
                    new InsertParam.Field("vector", Collections.singletonList(vector)),
                    new InsertParam.Field("content", Collections.singletonList(chunk)),
                    new InsertParam.Field("user_id", Collections.singletonList(userId)),
                    new InsertParam.Field("source_file", Collections.singletonList(sourceFile)),
                    new InsertParam.Field("chunk_index", Collections.singletonList(index))
            );

            milvusClientAdapter.upsert(
                    vectorProperties.getMilvus().getCollection(),
                    fields
            );
            index++;
        }
        log.info("✅ 文本存入 Milvus 完成，切片数：{}", chunks.size());
    }

    @Override
    public List<Map<String, Object>> search(String userId, String question, int topK) {
        List<Float> vector = createEmbedding(question);

        R<SearchResults> results = milvusClientAdapter.query(
                vectorProperties.getMilvus().getCollection(),
                vector,
                topK,
                userId
        );

        if (results.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("向量检索失败：" + results.getMessage());
        }
        SearchResults data = results.getData();
        if (data == null) {
            return Collections.emptyList();
        }
        // Milvus proto 里 results 为单个 SearchResultData，无 getResultsCount()/getResults(0)
        SearchResultData resultData = data.getResults();
        if (resultData.getScoresCount() == 0) {
            return Collections.emptyList();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(resultData);
        List<?> contents = wrapper.getFieldData("content", 0);
        List<?> sources = wrapper.getFieldData("source_file", 0);
        List<?> chunkIndices = wrapper.getFieldData("chunk_index", 0);
        @SuppressWarnings("unchecked")
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

        int n = idScores.size();
        List<Map<String, Object>> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> map = new HashMap<>();
            map.put("content", i < contents.size() ? contents.get(i) : null);
            map.put("source_file", i < sources.size() ? sources.get(i) : null);
            map.put("chunk_index", i < chunkIndices.size() ? chunkIndices.get(i) : null);
            map.put("score", idScores.get(i).getScore());
            list.add(map);
        }
        if (list.size() > topK) {
            log.warn("Milvus 返回 {} 条，大于请求的 topK={}，已截断", list.size(), topK);
            return new ArrayList<>(list.subList(0, topK));
        }
        return list;
    }

    private List<Float> parseEmbeddingResponse(Map<String, Object> body, boolean compatibleOpenAi) {
        if (compatibleOpenAi) {
            return parseOpenAiCompatibleEmbedding(body);
        }
        return parseDashScopeNativeEmbedding(body);
    }

    @SuppressWarnings("unchecked")
    private List<Float> parseOpenAiCompatibleEmbedding(Map<String, Object> body) {
        Object dataObj = body.get("data");
        if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty()) {
            throw new RuntimeException("嵌入接口返回 data 格式异常");
        }
        Object first = dataList.get(0);
        if (!(first instanceof Map)) {
            throw new RuntimeException("嵌入接口返回 data[0] 格式异常");
        }
        return toFloatList(((Map<String, Object>) first).get("embedding"));
    }

    @SuppressWarnings("unchecked")
    private List<Float> parseDashScopeNativeEmbedding(Map<String, Object> body) {
        Object code = body.get("code");
        if (code != null && StringUtils.hasText(String.valueOf(code))) {
            throw new RuntimeException("嵌入失败：" + body.get("message"));
        }
        Object sc = body.get("status_code");
        if (sc instanceof Number n && n.intValue() != 200) {
            throw new RuntimeException("嵌入失败 status_code=" + sc + "：" + body.get("message"));
        }
        Object outputObj = body.get("output");
        if (!(outputObj instanceof Map)) {
            throw new RuntimeException("嵌入接口无 output 或格式异常");
        }
        Map<String, Object> output = (Map<String, Object>) outputObj;
        Object embeddingsObj = output.get("embeddings");
        if (!(embeddingsObj instanceof List<?> emList) || emList.isEmpty()) {
            throw new RuntimeException("嵌入接口 output.embeddings 缺失或为空");
        }
        Object first = emList.get(0);
        if (!(first instanceof Map)) {
            throw new RuntimeException("嵌入接口 embeddings[0] 格式异常");
        }
        return toFloatList(((Map<String, Object>) first).get("embedding"));
    }

    private List<Float> toFloatList(Object embObj) {
        if (!(embObj instanceof List<?> embeddingList)) {
            throw new RuntimeException("嵌入向量字段缺失或类型异常");
        }
        List<Float> floatEmbedding = new ArrayList<>(embeddingList.size());
        for (Object o : embeddingList) {
            if (o instanceof Number n) {
                floatEmbedding.add(n.floatValue());
            } else {
                throw new RuntimeException("嵌入向量元素非数值类型");
            }
        }
        return floatEmbedding;
    }
}
