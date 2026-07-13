package com.ykx.backend.ai.lc;

import cn.hutool.core.util.StrUtil;
import com.ykx.backend.agent.AgentToolCallContext;
import com.ykx.backend.agent.AgentToolCallGuard;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.model.entity.AgentToolLogs;
import com.ykx.backend.model.vo.rag.RagContextVO;
import com.ykx.backend.service.RagService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.ykx.backend.service.AgentToolLogsService;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 工具：对个人知识库做补充向量检索。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>作为 LangChain4j Tool 暴露给模型调用；</li>
 *     <li>调用前做工具调用次数限制与重复 query 拦截；</li>
 *     <li>调用 RagService 检索当前用户个人知识库；</li>
 *     <li>记录工具调用日志，便于后续分析 Agent 行为。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTools {

    private static final String TOOL_NAME = "searchUserKnowledge";

    private final RagService ragService;
    private final AgentToolCallGuard agentToolCallGuard;
    private final AgentToolLogsService agentToolLogService;

    @Tool("当用户问题需要结合其个人知识库、已入库文档时再调用。query 为面向检索的简短中文或英文关键词。")
    public String searchUserKnowledge(String query) {
        long start = System.currentTimeMillis();
        String userId = UserContext.getUserId();
        String sessionId = AgentToolCallContext.get().getSessionId();

        if (!StringUtils.hasText(query)) {
            String output = "（未提供检索语句）";
            recordToolLog(userId, sessionId, query, output, false, "query 为空", start);
            return output;
        }

        if (!StringUtils.hasText(userId)) {
            String output = "（当前无登录用户，无法检索知识库）";
            recordToolLog(userId, sessionId, query, output, false, "userId 为空", start);
            return output;
        }

        String guardMessage = agentToolCallGuard.checkKnowledgeSearch(query);
        if (StringUtils.hasText(guardMessage)) {
            recordToolLog(userId, sessionId, query, guardMessage, false, "工具调用被限制", start);
            return guardMessage;
        }

        try {
            RagContextVO ctx = ragService.buildContext(userId, query.trim());
            String output = StringUtils.hasText(ctx.getContextText())
                    ? ctx.getContextText()
                    : "（知识库中未找到与本次查询高度相关的片段）";

            recordToolLog(userId, sessionId, query, output, true, null, start);
            return output;
        } catch (Exception e) {
            String output = "（知识库检索暂时不可用：" + e.getMessage() + "）";
            log.warn("工具 searchUserKnowledge 检索异常: {}", e.getMessage());
            recordToolLog(userId, sessionId, query, output, false, e.getMessage(), start);
            return output;
        }
    }

    private void recordToolLog(
            String userId,
            String sessionId,
            String query,
            String output,
            boolean success,
            String errorMessage,
            long start
    ) {
        try {
            long latencyMs = System.currentTimeMillis() - start;

            AgentToolLogs logRow = new AgentToolLogs();
            logRow.setId(UUID.randomUUID().toString());
            logRow.setUser_id(userId);
            logRow.setSession_id(sessionId);
            logRow.setTool_name(TOOL_NAME);
            logRow.setTool_input(StrUtil.subPre(query, 1000));
            logRow.setTool_output(StrUtil.subPre(output, 1000));
            logRow.setSuccess(success ? 1 : 0);
            logRow.setError_message(StrUtil.subPre(errorMessage, 1000));
            logRow.setLatency_ms(latencyMs);
            logRow.setCreated_at(LocalDateTime.now());
            agentToolLogService.save(logRow);
        } catch (Exception e) {
            log.warn("记录 Agent 工具调用日志失败: {}", e.getMessage());
        }
    }
}