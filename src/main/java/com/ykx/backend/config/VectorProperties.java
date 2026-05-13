package com.ykx.backend.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "vector")
public class VectorProperties {
    private Milvus milvus;  // 替换原Chroma配置
    private Chunk chunk;
    private Embedding embedding;

    @Data
    public static class Milvus {
        /** Milvus 2.3+ 数据库名，默认 default；写入自定义库如 my_rag 时填写 */
        private String database;
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String collection;
        private Integer dimension;
        private Long timeoutMs;
    }
    @Data
    public static class Chunk {
        private Integer size;
        private Integer overlap;
    }
    @Data
    public static class Embedding {
        private String model;
        private String apiKey;
        private String apiUrl;
    }
}
