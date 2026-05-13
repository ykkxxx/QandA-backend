package com.ykx.backend.service;
import java.util.List;
import java.util.Map;

/**
 * 向量服务接口
 */
public interface VectorService {

    /**
     * 调用DeepSeek生成向量
     */
    List<Float> createEmbedding(String text);

    /**
     * 文本切片
     */
    List<String> splitText(String text);

    /**
     * 保存文本到Milvus
     */
    void saveToMilvus(String userId, String content, String sourceFile);

    /**
     * 向量检索
     */
    List<Map<String, Object>> search(String userId, String question, int topK);
}