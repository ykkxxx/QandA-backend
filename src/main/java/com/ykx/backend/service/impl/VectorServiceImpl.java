package com.ykx.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykx.backend.config.VectorProperties;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.vo.rag.DocumentVO;
import com.ykx.backend.service.VectorService;
import com.ykx.backend.vector.adapter.MilvusClientAdapter;
import io.milvus.grpc.QueryResults;
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

import java.time.LocalDateTime;
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

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("texts", List.of(text));  // 百炼固定格式
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("model", vectorProperties.getEmbedding().getModel());
            param.put("input", input);

            //获取向量维度
            Integer dim = vectorProperties.getMilvus().getDimension();
            if (dim != null) {
                Map<String, Object> parameters = new LinkedHashMap<>();
                parameters.put("dimension", dim);
                param.put("parameters", parameters);
            }
            //发送请求
            //将对象转 字符串 http才能传输
            String jsonBody = objectMapper.writeValueAsString(param);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url.trim(), // 1. 目标地址
                    HttpMethod.POST,// 2. HTTP 动词/方法
                    entity,// 3. 请求实体（包含 Body 和 Headers）
                    String.class// 4. 响应体数据类型
            );
            //解析结果
            String raw = response.getBody();
            if (raw == null || raw.isEmpty()) {
                throw new RuntimeException("嵌入接口返回空");
            }
            Map<String, Object> body = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            return parseDashScopeNativeEmbedding(body); // 直接调用百炼解析
        } catch (Exception e) {
            throw new RuntimeException("生成向量失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<String> splitText(String text) {
        //字符个数
        int size = vectorProperties.getChunk().getSize();
        //段和上一段重复 的字符个数，
        int overlap = vectorProperties.getChunk().getOverlap();
        // 3. 计算步长：每次往后挪多少
        int step = size - overlap;
        //（如果重叠>=长度，就按不重叠处理）
        if (step <= 0) {
            step = size;
        }
        //// 用来装切好的所有片段
        List<String> list = new ArrayList<>();
        int len = text.length();
        for (int i = 0; i < len; i += step) {
            // // 结束位置 = 当前位置 + 每片长度（不能超过文本总长度）
            int end = Math.min(i + size, len);
            list.add(text.substring(i, end));
        }
        return list;
    }

    @Override
    public void saveToMilvus(String userId, String content, String sourceFile) {
        List<String> chunks = splitText(content);
        //记录当前文本片段（Chunk）在原始完整文本中的顺序位置。
        int index = 0;
        for (String chunk : chunks) {
            List<Float> vector = createEmbedding(chunk);
            String id = UUID.randomUUID().toString();
            //把单个值包成一个只有 1 个元素的列表Collections.singletonList
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
        log.info("文本存入 Milvus 完成，切片数：{}", chunks.size());
    }

    @Override
    public List<Map<String, Object>> search(String userId, String question, int topK) {
        //1.生成问题向量
        List<Float> vector = createEmbedding(question);
        //2.调用 Milvus 执行向量搜索
        R<SearchResults> results = milvusClientAdapter.query(
                vectorProperties.getMilvus().getCollection(),
                vector,
                topK,
                userId
        );
        //result
        // {
        //  "status": 0,          // 0=成功
        //  "message": "Success",
        //  "data": SearchResults对象  ← 真正的搜索结果
        //}
        //
        if (results.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("向量检索失败：" + results.getMessage());
        }
        SearchResults data = results.getData();
        if (data == null) {
            return Collections.emptyList();
        }
        //SearchResults {
        //  results: SearchResultData {  ← 真正的数据
        //    scores: [0.12, 0.15, 0.20],    ← 相似度分数（越小越相似）
        //    ids: ["1001","1002","1003"],   ← 主键ID
        //    fields_data: [
        //      Field {
        //        field_name: "content",
        //        data: ["文本片段1","文本片段2","文本片段3"]
        //      },
        //      Field {
        //        field_name: "source_file",
        //        data: ["文件1.pdf","文件2.md"]
        //      },
        //      Field {
        //        field_name: "chunk_index",
        //        data: [2, 3, 5]
        //      }
        //    ]
        //  }
        //}
        SearchResultData resultData = data.getResults();
        if (resultData.getScoresCount() == 0) {
            return Collections.emptyList();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(resultData);
        //将每一列的数据变成list
        //最终你得到了 4 个整齐的 List：
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
        //List<Map>
        // [
        //  {
        //    "content": "SpringBoot通过SPI机制实现自动配置……",
        //    "source_file": "Java面试.pdf",
        //    "chunk_index": 2,
        //    "score": 0.12
        //  },
        //  {
        //    "content": "@EnableAutoConfiguration注解加载配置类……",
        //    "source_file": "Java面试.pdf",
        //    "chunk_index": 3,
        //    "score": 0.15
        //  },
        //  {
        //    "content": "SpringBoot starter场景启动器原理……",
        //    "source_file": "Java面试.pdf",
        //    "chunk_index": 4,
        //    "score": 0.21
        //  }
        //]
        //分数越低 = 越相似
        if (list.size() > topK) {
            log.warn("Milvus 返回 {} 条，大于请求的 topK={}，已截断", list.size(), topK);
            return new ArrayList<>(list.subList(0, topK));
        }
        return list;
    }


    //获取返回结果中的 embedding 变成 List<Float>
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

    @Override
    public List<DocumentVO> listUserDocuments(String userId) {

        // 1. 参数校验
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId不能为空");
        }

        // 2. 根据用户id查询Milvus中的文档切片数据
        R<QueryResults> results = milvusClientAdapter.queryDocument(
                vectorProperties.getMilvus().getCollection(),
                userId
        );


        // 3. 查询失败或者没有数据，返回空集合
        if (results.getStatus() != R.Status.Success.getCode()
                || results.getData() == null) {
            return Collections.emptyList();
        }

        QueryResults queryResults = results.getData();

        List<String> sourceFilesList = null;
        List<String> documentIdsList = null;
        List<String> upload_timeList = null;

        // 4. 获取source_file字段
        for (var fieldData : queryResults.getFieldsDataList()) {
            String fieldName = fieldData.getFieldName();
            List<String> dataList = fieldData.getScalars().getStringData().getDataList();
            if ("source_file".equals(fieldName)) {
                sourceFilesList = dataList;
            }
            if("document_id".equals(fieldName)){
                documentIdsList = dataList;
            }
            if("upload_time".equals(fieldName)){
                upload_timeList = dataList;
            }
            if(sourceFilesList != null && documentIdsList != null && upload_timeList != null){
                break;
            }
        }

        // 没有查询到文件字段
        // 5. 完整长度校验：三组列表非空、长度完全一致
        if (sourceFilesList == null || sourceFilesList.isEmpty()
                || documentIdsList == null || documentIdsList.isEmpty()
                || upload_timeList == null || upload_timeList.isEmpty()
                || sourceFilesList.size() != documentIdsList.size()
                || sourceFilesList.size() != upload_timeList.size()) {
            return Collections.emptyList();
        }
        //去重逻辑
        Map<String, Object[]> documentPropertyMap = new LinkedHashMap<>();
        for (int i = 0; i < sourceFilesList.size(); i++) {
            String docId = documentIdsList.get(i);
            String fileName = sourceFilesList.get(i);
            String uploadTime = upload_timeList.get(i);

            if (documentPropertyMap.containsKey(docId)) {
                // 已有记录，切片数量+1
                Object[] arr = documentPropertyMap.get(docId);
                arr[2] = (Integer) arr[2] + 1;
            } else {
                // 首次存入：文件名、上传时间、初始数量1
                documentPropertyMap.put(docId, new Object[]{fileName, uploadTime, 1});
            }
        }


        /*
         * 6. 封装返回对象
         */
        List<DocumentVO> documentList = new ArrayList<>();


        for (Map.Entry<String,Object[]> entry : documentPropertyMap.entrySet()) {


            String documentId = entry.getKey();

            Object[] infoArr = entry.getValue();
            String documentName = (String) infoArr[0];
            String uploadTime = (String) infoArr[1];
            Integer chunkCount = (Integer) infoArr[2];

            DocumentVO vo = new DocumentVO();


            // 文档id（这里直接使用文件名，也可以改成数据库id）
            vo.setDocumentId(documentId);


            // 文档名称
            vo.setDocumentName(documentName);

            vo.setUploadTime(uploadTime);
            // 根据后缀判断类型
            if (documentName.toLowerCase().endsWith(".pdf")) {

                vo.setDocumentType("pdf");

            } else if (documentName.toLowerCase().endsWith(".txt")) {

                vo.setDocumentType("txt");

            } else {
                vo.setDocumentType("unknown");
            }


            // 文档包含多少切片
            vo.setChunkTotal(chunkCount);


            documentList.add(vo);
        }


        return documentList;
    }

    @Override
    public boolean deleteUserDocument(String userId, String documentId) {
        //1.校验参数
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId不能为空");
        }
        if (!StringUtils.hasText(documentId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "documentId不能为空");
        }
        //2.检查文档是否存在
        List<DocumentVO> documents = listUserDocuments(userId);
        boolean exists = documents.stream()
                .anyMatch(doc -> documentId.equals(doc.getDocumentId()));

        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        //3.找到符合的userId 和documentId相同所有chunck
        boolean success = milvusClientAdapter.deleteDocument(
                vectorProperties.getMilvus().getCollection(),
                userId,
                documentId
        );
        // 4. 返回结果
        return success;
    }

}
