package com.ykx.backend.agent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.config.BochaProperties;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTools {

    private final BochaProperties bochaProperties;

    @Tool("当需要搜索网络信息时调用此工具。输入搜索关键词，返回搜索结果摘要。适用于获取最新的旅游攻略、景点推荐、新闻资讯等信息。")
    public String webSearch(String query) {
        String userId = UserContext.getUserId();
        log.info("Web search for user {}: {}", userId, query);

        if (StrUtil.isBlank(query)) {
            return "搜索关键词为空";
        }

        if (StrUtil.isBlank(bochaProperties.getApiKey())) {
            log.warn("Bocha API key not configured");
            return "搜索服务未配置，请联系管理员";
        }

        try {
            return search(query);
        } catch (Exception e) {
            log.error("Web search failed", e);
            return "搜索失败：" + e.getMessage();
        }
    }

    private String search(String query) {
        JSONObject payload = JSONUtil.createObj()
                .put("query", query)
                .put("count", bochaProperties.getCount())
                .put("summary", true);

        try (HttpResponse response = HttpRequest.post(bochaProperties.getApiUrl())
                .header("Authorization", "Bearer " + bochaProperties.getApiKey())
                .header("Content-Type", "application/json")
                .body(payload.toString())
                .timeout(30000)
                .execute()) {

            if (!response.isOk()) {
                return "搜索失败，HTTP状态码：" + response.getStatus();
            }

            String body = response.body();
            JSONObject result = JSONUtil.parseObj(body);

            int code = result.getInt("code", -1);
            if (code != 200) {
                String errorMsg = result.getStr("msg", "未知错误");
                log.error("Bocha API returned error: code={}, msg={}, body={}", code, errorMsg, body);
                return "搜索失败：" + errorMsg + "（错误码：" + code + "）";
            }

            JSONObject data = result.getJSONObject("data");
            if (data == null) {
                return "无搜索结果";
            }

            JSONObject webPages = data.getJSONObject("webPages");
            if (webPages == null) {
                return "无搜索结果";
            }

            JSONArray values = webPages.getJSONArray("value");
            if (values == null || values.isEmpty()) {
                return "无搜索结果";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("🔍 搜索结果（共 ").append(values.size()).append(" 条）：\n\n");

            for (int i = 0; i < Math.min(values.size(), bochaProperties.getCount()); i++) {
                JSONObject item = values.getJSONObject(i);
                String name = item.getStr("name", "");
                String url = item.getStr("url", "");
                String summary = item.getStr("summary", item.getStr("snippet", ""));
                String siteName = item.getStr("siteName", "");
                String datePublished = item.getStr("datePublished", "");

                sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("【").append(i + 1).append("】 ").append(name).append("\n");
                
                if (!siteName.isEmpty()) {
                    sb.append("📌 来源：").append(siteName).append("\n");
                }
                if (!datePublished.isEmpty()) {
                    sb.append("📅 时间：").append(datePublished.substring(0, 10)).append("\n");
                }
                if (!summary.isEmpty()) {
                    sb.append("\n📝 内容摘要：\n");
                    sb.append(formatSummary(summary)).append("\n");
                }
                if (!url.isEmpty()) {
                    sb.append("\n🔗 链接：").append(url).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("Bocha search API call failed", e);
            return "搜索失败：" + e.getMessage();
        }
    }

    private String formatSummary(String summary) {
        if (StrUtil.isBlank(summary)) {
            return "";
        }
        
        int maxLength = 200;
        if (summary.length() > maxLength) {
            summary = summary.substring(0, maxLength).trim() + "...";
        }
        
        summary = summary.replaceAll("\\s+", " ").trim();
        
        StringBuilder formatted = new StringBuilder();
        int lineLength = 60;
        for (int i = 0; i < summary.length(); i += lineLength) {
            if (i > 0) {
                formatted.append("\n    ");
            }
            formatted.append(summary.substring(i, Math.min(i + lineLength, summary.length())));
        }
        
        return formatted.toString();
    }
}