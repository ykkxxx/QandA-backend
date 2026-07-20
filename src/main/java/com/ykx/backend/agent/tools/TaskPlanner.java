package com.ykx.backend.agent.tools;

import cn.hutool.core.util.StrUtil;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.config.DeepSeekAiProperties;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPlanner {

    private final ChatLanguageModel chatLanguageModel;
    private final DeepSeekAiProperties deepSeekAiProperties;

    @Tool("当用户提出复杂任务（包含多个步骤或需要多个工具配合）时调用此工具。输入用户的原始问题，返回结构化的任务计划JSON数组。")
    public String planTask(String userQuery) {
        String userId = UserContext.getUserId();
        log.info("Task planning for user {}: {}", userId, userQuery);

        if (StrUtil.isBlank(userQuery)) {
            return "{\"tasks\": [], \"error\": \"查询内容为空\"}";
        }

        try {
            String plan = generatePlanByLlm(userQuery);
            log.info("Generated plan: {}", plan);
            return plan;
        } catch (Exception e) {
            log.error("Task planning failed", e);
            return "{\"tasks\": [], \"error\": \"任务规划失败：" + e.getMessage() + "\"}";
        }
    }

    private String generatePlanByLlm(String userQuery) {
        SystemMessage systemMessage = SystemMessage.from("""
            你是一个专业的任务规划专家。请将用户的问题分解为一系列可执行的子任务。
            
            任务分解要求：
            1. 分析用户问题是否需要多个步骤完成
            2. 如果需要，拆分为 2-5 个简单子任务
            3. 每个子任务应该是独立可执行的
            4. 考虑任务之间的依赖关系和执行顺序
            5. 每个任务的 description 要简洁明了
            6. 必须返回纯 JSON 格式，不要包含其他文字
            
            返回格式示例：
            {
                "tasks": [
                    {"id": 1, "description": "查询天气信息", "tool": "getWeather", "params": {"city": "北京"}},
                    {"id": 2, "description": "搜索附近景点", "tool": "searchPoiByAddress", "params": {"address": "北京", "keywords": "景点"}}
                ]
            }
            
            如果问题很简单，不需要分解，可以返回空数组：
            {"tasks": []}
            
            可用工具列表：
            - getWeather(city): 查询指定城市的天气。city 必须是具体城市名（如昆明、北京），不能是省份
            - searchPoiByAddress(address, keywords, radius): 搜索指定地址附近的POI（景点、美食等）
              - address 必须是具体城市名（如昆明、大理），不能是省份或模糊地址
              - keywords 应该是简单名词（如景点、美食、酒店），不要包含形容词
              - radius 建议使用较大值（如 10000 表示10公里）
            - webSearch(query): 搜索网络信息，用于获取最新的旅游攻略、景点推荐等信息
            - searchUserKnowledge(query): 检索用户的个人知识库
            - list(): 获取用户的文档列表
            - delete(documentId): 删除用户的指定文档
            
            工具调用规则：
            1. 对于全省范围的查询，应该拆分为多个城市分别查询
               例如：查询云南的景点 → 分别查询昆明、大理、丽江的景点
            2. 如果 POI 搜索可能没有结果（如小众景点、特定需求），应该优先使用 webSearch
            3. 天气查询只能针对具体城市，不能查询省份
            4. 复杂问题（如旅游规划）应该结合多个工具使用：
               - getWeather 获取天气信息
               - searchPoiByAddress 获取当地景点和餐饮
               - webSearch 获取最新攻略和推荐
               """);

        UserMessage userMessage = UserMessage.from("用户问题：" + userQuery);

        return chatLanguageModel.generate(systemMessage, userMessage).content().text();
    }
}