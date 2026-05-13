package com.ykx.backend.rag.prompt;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * RAG 提示词拼装：检索上下文块拼接、用户消息构造。
 */
public final class RagPromptBuilder {

    private RagPromptBuilder() {
    }

    /**
     * 将多条检索片段格式化为带编号的上下文块（供模型阅读）。
     */
    public static String concatRetrievedChunks(List<Map<String, Object>> chunks, int maxChars) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n\n");
        int used = 0;
        int n = 1;
        for (Map<String, Object> row : chunks) {
            String block = formatOneChunk(n, row);
            if (!StringUtils.hasText(block)) {
                continue;
            }
            if (used + block.length() > maxChars && used > 0) {
                break;
            }
            joiner.add(block);
            used += block.length() + 2;
            n++;
        }
        return joiner.toString();
    }

    private static String formatOneChunk(int index, Map<String, Object> row) {
        Object content = row.get("content");
        if (content == null) {
            return "";
        }
        String text = String.valueOf(content).trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[片段").append(index).append("]\n");
        Object src = row.get("source_file");
        if (src != null && StringUtils.hasText(String.valueOf(src))) {
            sb.append("来源：").append(src).append("\n");
        }
        sb.append(text);
        return sb.toString();
    }

    /**
     * 构造发给大模型的用户侧正文：问题 + 检索到的参考材料说明。
     */
    public static String buildRagUserContent(String question, String retrievedContext) {
        String q = question == null ? "" : question.trim();
        String ctx = retrievedContext == null ? "" : retrievedContext.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("请严格依据下方「参考资料」回答用户问题；若资料中没有答案，请明确说明无法从资料中得出，不要编造。\n\n");
        sb.append("【用户问题】\n").append(q).append("\n\n");
        if (StringUtils.hasText(ctx)) {
            sb.append("【参考资料】\n").append(ctx);
        } else {
            sb.append("【参考资料】\n（无匹配片段）");
        }
        return sb.toString();
    }
}
