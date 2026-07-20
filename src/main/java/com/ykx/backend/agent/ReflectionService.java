// 文件路径: src/main/java/com/ykx/backend/agent/ReflectionService.java

package com.ykx.backend.agent;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionService {

    private final ChatLanguageModel chatLanguageModel;

    public ReflectionResult reflect(String userQuery, String planJson, String executionResults) {
        log.info("Starting reflection for query: {}", userQuery);

        String systemPrompt = """
            你是一个专业的反思评估专家。请对以下任务执行过程进行评估。
            
            评估标准：
            1. 任务规划是否合理（是否覆盖了用户的所有需求）
            2. 工具调用是否正确（参数是否正确，返回结果是否有效）
            3. 执行结果是否完整（是否回答了用户的问题）
            4. 是否存在错误或遗漏
            
            请返回JSON格式：
            {
                "score": 0-100,
                "suggestion": "改进建议",
                "needsCorrection": true/false,
                "correctionPlan": ["修正步骤1", "修正步骤2"]
            }
            """;

        String userPrompt = """
            用户问题：%s
            
            任务计划：%s
            
            执行结果：%s
            """.formatted(userQuery, planJson, executionResults);

        try {
            SystemMessage systemMessage = SystemMessage.from(systemPrompt);
            UserMessage userMessage = UserMessage.from(userPrompt);
            String result = chatLanguageModel.generate(systemMessage, userMessage).content().text();

            log.info("Reflection result raw: {}", result);

            int startIdx = result.indexOf('{');
            int endIdx = result.lastIndexOf('}');
            if (startIdx == -1 || endIdx == -1 || endIdx < startIdx) {
                log.warn("Reflection result is not valid JSON: {}", result);
                return ReflectionResult.builder()
                        .score(80)
                        .suggestion("反思评估结果格式异常，使用默认结果")
                        .needsCorrection(false)
                        .build();
            }

            String jsonStr = result.substring(startIdx, endIdx + 1);
            JSONObject json = JSONUtil.parseObj(jsonStr);
            
            JSONArray correctionPlan = json.getJSONArray("correctionPlan");
            if (correctionPlan == null || correctionPlan.isEmpty()) {
                return ReflectionResult.builder()
                        .score(json.getInt("score", 80))
                        .suggestion(json.getStr("suggestion", ""))
                        .needsCorrection(false)
                        .build();
            }

            return ReflectionResult.builder()
                    .score(json.getInt("score", 80))
                    .suggestion(json.getStr("suggestion", ""))
                    .needsCorrection(json.getBool("needsCorrection", false))
                    .correctionPlan(correctionPlan)
                    .build();
        } catch (Exception e) {
            log.error("Reflection failed", e);
            return ReflectionResult.builder()
                    .score(80)
                    .suggestion("反思评估失败，使用默认结果")
                    .needsCorrection(false)
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class ReflectionResult {
        private int score;
        private String suggestion;
        private boolean needsCorrection;
        private JSONArray correctionPlan;
    }
}
