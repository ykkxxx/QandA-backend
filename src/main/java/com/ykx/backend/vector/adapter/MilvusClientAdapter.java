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
 * 这是一个操作 Milvus 向量数据库的工具类
 * 连接库 → 创建表 → 存文本分片 + 向量 → 按向量相似度搜索
 */
@Slf4j
@Component
public class MilvusClientAdapter {

    @Resource
    private VectorProperties vectorProperties;

    private MilvusServiceClient milvusClient;

    /** 获取milvus数据库库名 */
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
    //连接数据库
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

    /**
     *
     *
     * @param collectionName 表名
     */
    public void ensureCollection(String collectionName) {
        String db = milvusDatabaseName();
        // HasCollectionParam.Builder 是 Milvus 官方提供的「参数构造器」
        //1.判断这个表是否存在
        //hasB 可以看出一张表[
        // 表名：
        // 库名：
        // ]
        HasCollectionParam.Builder hasB = HasCollectionParam.newBuilder()
                .withCollectionName(collectionName);
        if (db != null) {
            //如果db存在 则填入指定库名 不然用默认库
            hasB.withDatabaseName(db);
        }
        //R 就是 Milvus 官方写的 BaseResponse！
        R<Boolean> checkCollection = milvusClient.hasCollection(hasB.build());
        //如果是data是空 说明没有这张表 则重新创建表
        if (!checkCollection.getData()) {
            //2. 定义表的字段
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
            //4. 创建向量索引信息
            CreateIndexParam.Builder indexB = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("vector")
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.L2)
                    .withExtraParam("{\"nlist\":1024}");
            if (db != null) {
                indexB.withDatabaseName(db);
            }
            //创建索引
            R<?> indexR = milvusClient.createIndex(indexB.build());
            if (indexR.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("❌ Milvus 创建索引失败：" + indexR.getMessage());
            }
            //Milvus 有一个硬性规则：
            //创建完表 → 必须 load 加载到内存 → 才能搜索、插入数据！
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
    //collectionName：要插入的表名
    //fields：要插入的数据（id、向量、文本、用户 ID 等）
    public void upsert(String collectionName, List<InsertParam.Field> fields) {
        String db = milvusDatabaseName();
        //3. 构建插入参数
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
        //7. 刷新数据（让数据立刻可搜索）
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
    //根据问题 返回topK相似的chunk
    //collectionName 表名
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
