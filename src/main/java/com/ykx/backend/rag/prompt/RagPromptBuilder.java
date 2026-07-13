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
     * 拼接 RAG 检索出来的片段
     * 一边拼一边算长度，超过 maxChars 就停止
     * 保证最终 Prompt 不超长
     */
    public static String concatRetrievedChunks(
            List<Map<String, Object>> chunks,  // 精排后的片段列表（最相关的排前面）
            int maxChars                      // 最大字符数：例如 8000
    ) {

        // 1. 空值直接返回空
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        // 2. 拼接工具，每个片段之间用 两个换行 隔开
        StringJoiner joiner = new StringJoiner("\n\n");

        int used = 0;  // 已经用了多少字符
        int n = 1;     // 片段编号 1、2、3…


        // 3. 遍历每一个片段（已经按相关性排序）
        for (Map<String, Object> row : chunks) {

            // 把 1 条片段格式化成：
            // 【片段1】xxxx
            // 【片段2】xxxx
            String block = formatOneChunk(n, row);

            // 空片段跳过
            if (!StringUtils.hasText(block)) {
                continue;
            }


            // ==============================================
            // 🔥 核心截断逻辑：
            // 如果加上这个片段会超长度 → 直接停止，不添加！
            // ==============================================
            //used>0不让第一条片段因为 “太长” 直接被丢掉，导致上下文空！
            if (used + block.length() > maxChars && used > 0) {
                break;
            }

            // 4. 加入拼接结果
            joiner.add(block);

            // 5. 累计已用字符数
            used += block.length() + 2;  // +2 是分隔符 \n\n

            n++; // 片段编号+1
        }

        // 6. 返回最终拼接好的上下文字符串
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
