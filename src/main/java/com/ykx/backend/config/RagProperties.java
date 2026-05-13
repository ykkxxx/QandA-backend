package com.ykx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG：召回条数、向量距离过滤、重排与上下文长度。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Retrieval retrieval = new Retrieval();
    private VectorFilter vector = new VectorFilter();
    private Rerank rerank = new Rerank();
    private Prompt prompt = new Prompt();

    @Data
    public static class Retrieval {
        /** Milvus 首次向量召回条数（大于最终进入上下文的条数，供重排） */
        private int initialTopK = 20;
    }

    @Data
    public static class VectorFilter {
        /**
         * L2 距离上限（与 Milvus 检索 score 一致，数值越大越不相似）。
         * 为 null 时不做向量侧过滤。
         */
        private Double maxL2Distance;
    }

    @Data
    public static class Rerank {
        private boolean enabled = true;
        /** gte-rerank-v2 走原生路径；qwen3-rerank 可走 compatible-api */
        private String model = "gte-rerank-v2";
        private String apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        /** 为空时使用 vector.embedding.api-key */
        private String apiKey;
        /** 重排分低于此值的文档丢弃（gte/qwen3 均为 0~1，越大越相关） */
        private double minRelevanceScore = 0.25;
        /** 重排并过滤后，最多保留几条写入上下文 */
        private int finalTopK = 5;
    }

    @Data
    public static class Prompt {
        /** 拼接进提示词的检索正文总长度上限（字符） */
        private int maxContextChars = 8000;
    }
}
