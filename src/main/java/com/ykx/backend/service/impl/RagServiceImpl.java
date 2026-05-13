package com.ykx.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ykx.backend.config.RagProperties;
import com.ykx.backend.config.VectorProperties;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.vo.rag.RagContextVO;
import com.ykx.backend.rag.prompt.RagPromptBuilder;
import com.ykx.backend.service.RagService;
import com.ykx.backend.service.ReorderService;
import com.ykx.backend.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorService vectorService;
    private final ReorderService reorderService;
    private final RagProperties ragProperties;
    private final VectorProperties vectorProperties;

    @Override
    public RagContextVO buildContext(String userId, String question) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }
        if (StrUtil.isBlank(question)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题不能为空");
        }

        int initialK = Math.max(1,
                ragProperties.getRetrieval() != null
                        ? ragProperties.getRetrieval().getInitialTopK()
                        : 20);
        List<Map<String, Object>> hits = vectorService.search(userId, question.trim(), initialK);
        if (hits.isEmpty()) {
            String db = vectorProperties.getMilvus() != null
                    ? vectorProperties.getMilvus().getDatabase()
                    : null;
            String coll = vectorProperties.getMilvus() != null
                    ? vectorProperties.getMilvus().getCollection()
                    : null;
            log.info(
                    "RAG：向量检索 0 条。当前 userId={}（来自登录 JWT），须与入库 /vector/save/text 的 userId 完全一致；Milvus database={} collection={}",
                    userId,
                    db != null ? db : "default",
                    coll != null ? coll : "?");
        }

        Double maxL2 = ragProperties.getVector() != null
                ? ragProperties.getVector().getMaxL2Distance()
                : null;
        if (maxL2 != null && !hits.isEmpty()) {
            int before = hits.size();
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> h : hits) {
                double d = extractVectorDistance(h);
                if (d <= maxL2) {
                    filtered.add(h);
                }
            }
            hits = filtered;
            if (hits.isEmpty() && before > 0) {
                log.info("RAG：向量命中 {} 条，均被 rag.vector.max-l2-distance={} 过滤", before, maxL2);
            }
        }

        List<Map<String, Object>> ranked = reorderService.rerank(question.trim(), hits);

        List<Map<String, Object>> normalized = new ArrayList<>(ranked.size());
        for (Map<String, Object> row : ranked) {
            normalized.add(new HashMap<>(row));
        }

        int maxChars = Math.max(500,
                ragProperties.getPrompt() != null
                        ? ragProperties.getPrompt().getMaxContextChars()
                        : 8000);
        String contextText = RagPromptBuilder.concatRetrievedChunks(normalized, maxChars);
        String llmUserContent = RagPromptBuilder.buildRagUserContent(question.trim(), contextText);

        RagContextVO vo = new RagContextVO();
        vo.setChunks(normalized);
        vo.setContextText(contextText);
        vo.setLlmUserContent(llmUserContent);
        return vo;
    }

    private static double extractVectorDistance(Map<String, Object> h) {
        Object s = h.get("score");
        if (s instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(s));
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }
}
