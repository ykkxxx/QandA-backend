package com.ykx.backend.agent;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ykx.backend.agent.tools.DocumentManagementTools;
import com.ykx.backend.agent.tools.KnowledgeTools;
import com.ykx.backend.agent.tools.LocationTools;
import com.ykx.backend.agent.tools.WebSearchTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final KnowledgeTools knowledgeTools;
    private final LocationTools locationTools;
    private final DocumentManagementTools documentManagementTools;
    private final WebSearchTools webSearchTools;

    public String executeAllTasks(String planJson) {
        try {
            JSONObject plan = JSONUtil.parseObj(planJson);
            JSONArray tasks = plan.getJSONArray("tasks");

            if (tasks == null || tasks.isEmpty()) {
                return "";
            }

            List<String> results = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                String result = executeTask(task);
                results.add(result);
            }

            return String.join("\n\n", results);
        } catch (Exception e) {
            log.error("Task execution failed", e);
            return "任务执行失败：" + e.getMessage();
        }
    }

    private String executeTask(JSONObject task) {
        String toolName = task.getStr("tool");
        String description = task.getStr("description");
        JSONObject params = task.getJSONObject("params");

        log.info("Executing task: {} (tool: {})", description, toolName);

        try {
            String rawResult = switch (toolName) {
                case "getWeather" -> locationTools.getWeather(params.getStr("city"));
                case "searchPoiByAddress" -> locationTools.searchPoiByAddress(
                        params.getStr("address"),
                        params.getStr("keywords"),
                        params.getInt("radius", 5000)
                );
                case "webSearch" -> webSearchTools.webSearch(params.getStr("query"));
                case "searchUserKnowledge" -> knowledgeTools.searchUserKnowledge(params.getStr("query"));
                case "list" -> documentManagementTools.list();
                case "delete" -> documentManagementTools.delete(params.getStr("documentId"));
                default -> "未知工具：" + toolName;
            };

            String formattedResult = formatResult(toolName, rawResult);

            task.set("status", "completed");
            task.set("output", rawResult);
            return "✓ " + description + "\n" + formattedResult;
        } catch (Exception e) {
            task.set("status", "failed");
            task.set("error", e.getMessage());
            log.error("Task failed: {}", description, e);
            return "✗ " + description + "\n错误：" + e.getMessage();
        }
    }

    private String formatResult(String toolName, String rawResult) {
        if (rawResult == null || rawResult.isEmpty()) {
            return "无结果";
        }

        try {
            return switch (toolName) {
                case "getWeather" -> formatWeather(rawResult);
                case "searchPoiByAddress" -> formatPoi(rawResult);
                case "webSearch" -> formatWebSearch(rawResult);
                case "searchUserKnowledge" -> formatKnowledge(rawResult);
                case "list" -> formatDocumentList(rawResult);
                default -> rawResult;
            };
        } catch (Exception e) {
            log.warn("Formatting failed for tool {}: {}", toolName, e.getMessage());
            return rawResult;
        }
    }

    private String formatWeather(String rawResult) {
        JSONObject json = JSONUtil.parseObj(rawResult);
        if (!"1".equals(json.getStr("status"))) {
            return "查询失败：" + json.getStr("info", "未知错误");
        }

        JSONArray forecasts = json.getJSONArray("forecasts");
        if (forecasts == null || forecasts.isEmpty()) {
            return "无天气数据";
        }

        JSONObject forecast = forecasts.getJSONObject(0);
        String city = forecast.getStr("city");
        JSONArray casts = forecast.getJSONArray("casts");

        StringBuilder sb = new StringBuilder();
        sb.append(city).append("天气预报：\n");
        sb.append("| 日期 | 天气 | 温度 |\n");
        sb.append("|------|------|------|\n");

        for (int i = 0; i < Math.min(casts.size(), 4); i++) {
            JSONObject cast = casts.getJSONObject(i);
            String date = cast.getStr("date").substring(5);
            String weather = cast.getStr("dayweather");
            String temp = cast.getStr("daytemp") + "°C / " + cast.getStr("nighttemp") + "°C";
            sb.append("| ").append(date).append(" | ").append(weather).append(" | ").append(temp).append(" |\n");
        }

        return sb.toString();
    }

    private String formatPoi(String rawResult) {
        JSONObject json = JSONUtil.parseObj(rawResult);
        if (!"1".equals(json.getStr("status"))) {
            return "查询失败：" + json.getStr("info", "未知错误");
        }

        JSONArray pois = json.getJSONArray("pois");
        if (pois == null || pois.isEmpty()) {
            return "无匹配景点";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("推荐景点：\n");

        for (int i = 0; i < Math.min(pois.size(), 5); i++) {
            JSONObject poi = pois.getJSONObject(i);
            String name = poi.getStr("name");
            String address = poi.getStr("address", "");
            String type = poi.getStr("type", "");
            sb.append((i + 1)).append(". ").append(name);
            if (!address.isEmpty()) {
                sb.append("（").append(address).append("）");
            }
            if (!type.isEmpty()) {
                sb.append(" - ").append(type);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatWebSearch(String rawResult) {
        return rawResult;
    }

    private String formatKnowledge(String rawResult) {
        return rawResult;
    }

    private String formatDocumentList(String rawResult) {
        return rawResult;
    }

    public String summarize(String planJson, String taskResults) {
        try {
            JSONObject plan = JSONUtil.parseObj(planJson);
            JSONArray tasks = plan.getJSONArray("tasks");

            if (tasks == null || tasks.isEmpty()) {
                return taskResults;
            }

            StringBuilder summary = new StringBuilder();
            
            summary.append("📋 任务拆解：\n");
            for (int i = 0; i < tasks.size(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                String description = task.getStr("description");
                String tool = task.getStr("tool");
                summary.append("  ").append(i + 1).append(". ").append(description);
                if (tool != null && !tool.isEmpty()) {
                    summary.append(" (工具: ").append(tool).append(")");
                }
                summary.append("\n");
            }
            summary.append("\n");

            summary.append("✅ 执行结果：\n");
            summary.append(taskResults);

            return summary.toString();
        } catch (Exception e) {
            log.error("Summarization failed", e);
            return taskResults;
        }
    }
}