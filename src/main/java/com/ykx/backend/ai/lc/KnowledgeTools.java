package com.ykx.backend.ai.lc;

import com.ykx.backend.common.UserContext;
import com.ykx.backend.model.vo.rag.RagContextVO;
import com.ykx.backend.service.RagService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Agent 工具：对个人知识库做补充向量检索（简化单工具）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTools {

    private final RagService ragService;

    @Tool("当用户问题需要结合其个人知识库、已入库文档时再调用。query 为面向检索的简短中文或英文关键词。")
    public String searchUserKnowledge(String query) {
        if (!StringUtils.hasText(query)) {
            return "（未提供检索语句）";
        }
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            return "（当前无登录用户，无法检索知识库）";
        }
        try {
            RagContextVO ctx = ragService.buildContext(userId, query.trim());
            if (!StringUtils.hasText(ctx.getContextText())) {
                return "（知识库中未找到与本次查询高度相关的片段）";
            }
            return ctx.getContextText();
        } catch (Exception e) {
            log.warn("工具 searchUserKnowledge 检索异常: {}", e.getMessage());
            return "（知识库检索暂时不可用：" + e.getMessage() + "）";
        }
    }
}
