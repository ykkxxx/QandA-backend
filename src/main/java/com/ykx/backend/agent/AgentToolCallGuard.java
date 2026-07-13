package com.ykx.backend.agent;

import com.ykx.backend.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AgentToolCallGuard {

    private final AgentProperties agentProperties;

    public String checkKnowledgeSearch(String query) {
        AgentToolCallContext.State state = AgentToolCallContext.get();
        if (state == null) {
            return null;
        }

        if (state.getTotalToolCalls() >= agentProperties.getMaxToolCalls()) {
            return "（工具调用次数已达上限，请基于已有信息回答；如资料不足，请明确说明。）";
        }

        if (state.getKnowledgeSearchCalls() >= agentProperties.getMaxKnowledgeSearchCalls()) {
            return "（知识库检索次数已达上限，请基于已有检索结果回答；如资料不足，请明确说明。）";
        }

        String normalizedQuery = normalize(query);
        if (state.getCalledKnowledgeQueries().contains(normalizedQuery)) {
            return "（该问题已用相似查询检索过，请勿重复检索；请基于已有资料回答。）";
        }

        state.increaseTotalToolCalls();
        state.increaseKnowledgeSearchCalls();
        state.getCalledKnowledgeQueries().add(normalizedQuery);
        return null;
    }
    //去掉query中的空格 大小写统一 方便知道是同一个问题
    private static String normalize(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        return query.trim()
                .toLowerCase()
                .replaceAll("\\s+", "");
    }
}