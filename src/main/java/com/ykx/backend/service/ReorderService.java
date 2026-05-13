package com.ykx.backend.service;

import java.util.List;
import java.util.Map;

/**
 * 对向量召回结果调用重排模型二次打分、排序与截断。
 */
public interface ReorderService {

    /**
     * @param query 用户问题
     * @param hits  {@link com.ykx.backend.service.VectorService#search} 返回的列表（须含 content）
     * @return 按重排分降序的结果；字段中增加 {@code rerank_score}；重排关闭或失败时可能原样截断返回
     */
    List<Map<String, Object>> rerank(String query, List<Map<String, Object>> hits);
}
