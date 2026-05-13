package com.ykx.backend.vector.adapter;

import com.ykx.backend.config.VectorProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.FlushResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Milvus 客户端适配器（SDK 2.4.x，支持 database + collection）
 */
@Slf4j
@Component
public class MilvusClientAdapter {

    @Resource
    private VectorProperties vectorProperties;

    private MilvusServiceClient milvusClient;

    /** 配置了则所有 RPC 带库名；未配置则走服务端默认库 default */
    private String milvusDatabaseName() {
        if (vectorProperties.getMilvus() == null) {
            return null;
        }
        String db = vectorProperties.getMilvus().getDatabase();
        if (!StringUtils.hasText(db)) {
            return null;
        }
        return db.trim();
    }

    @PostConstruct
    public void init() {
        try {
            ConnectParam.Builder conn = ConnectParam.newBuilder()
                    .withHost(vectorProperties.getMilvus().getHost())
                    .withPort(vectorProperties.getMilvus().getPort());
            // 库名不在 ConnectParam 上设置（部分 SDK 无该方法），由各 RPC 的 withDatabaseName 指定。
            milvusClient = new MilvusServiceClient(conn.build());

            String db = milvusDatabaseName();
            log.info("✅ Milvus 连接成功，请求级 database={}", db != null ? db : "default");
            ensureCollection(vectorProperties.getMilvus().getCollection());

        } catch (Exception e) {
            throw new RuntimeException("❌ Milvus 连接失败：" + e.getMessage(), e);
        }
    }

    public void ensureCollection(String collectionName) {
        String db = milvusDatabaseName();

        HasCollectionParam.Builder hasB = HasCollectionParam.newBuilder()
                .withCollectionName(collectionName);
        if (db != null) {
            hasB.withDatabaseName(db);
        }
        R<Boolean> checkCollection = milvusClient.hasCollection(hasB.build());

        if (!checkCollection.getData()) {
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .withPrimaryKey(true)
                    .build();

            FieldType vectorField = FieldType.newBuilder()
                    .withName("vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(vectorProperties.getMilvus().getDimension())
                    .build();

            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(1024)
                    .build();

            FieldType userIdField = FieldType.newBuilder()
                    .withName("user_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .build();

            FieldType sourceField = FieldType.newBuilder()
                    .withName("source_file")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(256)
                    .build();

            FieldType chunkIndexField = FieldType.newBuilder()
                    .withName("chunk_index")
                    .withDataType(DataType.Int32)
                    .build();

            CreateCollectionParam.Builder createB = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .addFieldType(idField)
                    .addFieldType(vectorField)
                    .addFieldType(contentField)
                    .addFieldType(userIdField)
                    .addFieldType(sourceField)
                    .addFieldType(chunkIndexField);
            if (db != null) {
                createB.withDatabaseName(db);
            }
            milvusClient.createCollection(createB.build());

            CreateIndexParam.Builder indexB = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("vector")
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.L2)
                    .withExtraParam("{\"nlist\":1024}");
            if (db != null) {
                indexB.withDatabaseName(db);
            }
            R<?> indexR = milvusClient.createIndex(indexB.build());
            if (indexR.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("❌ Milvus 创建索引失败：" + indexR.getMessage());
            }

            LoadCollectionParam.Builder loadB = LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName);
            if (db != null) {
                loadB.withDatabaseName(db);
            }
            R<?> loadR = milvusClient.loadCollection(loadB.build());
            if (loadR.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("❌ Milvus 加载集合失败：" + loadR.getMessage());
            }

            log.info("✅ Milvus 集合创建成功：{}.{}", db != null ? db : "default", collectionName);
        }
    }

    public void upsert(String collectionName, List<InsertParam.Field> fields) {
        String db = milvusDatabaseName();

        InsertParam.Builder insB = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields);
        if (db != null) {
            insB.withDatabaseName(db);
        }
        R<MutationResult> result = milvusClient.insert(insB.build());

        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("❌ 向量入库失败：" + result.getMessage());
        }

        FlushParam.Builder flushB = FlushParam.newBuilder()
                .addCollectionName(collectionName)
                .withSyncFlush(Boolean.TRUE);
        if (db != null) {
            flushB.withDatabaseName(db);
        }
        R<FlushResponse> flushR = milvusClient.flush(flushB.build());
        if (flushR.getStatus() != R.Status.Success.getCode()) {
            log.warn("Milvus flush 未成功：{}", flushR.getMessage());
        }

        log.info("✅ 向量入库完成：{} 条", result.getData().getInsertCnt());
    }

    public R<SearchResults> query(String collectionName, List<Float> embedding, int topK, String userId) {
        String db = milvusDatabaseName();

        SearchParam.Builder searchB = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withFloatVectors(List.of(embedding))
                .withTopK(topK)
                .withVectorFieldName("vector")
                .withMetricType(MetricType.L2)
                .withOutFields(List.of("content", "source_file", "chunk_index"))
                .withExpr("user_id == '" + userId + "'")
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG);
        if (db != null) {
            searchB.withDatabaseName(db);
        }

        return milvusClient.search(searchB.build());
    }
}
