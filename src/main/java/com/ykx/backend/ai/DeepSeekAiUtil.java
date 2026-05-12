package com.ykx.backend.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ykx.backend.config.DeepSeekAiProperties;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI 工具类（封装AI调用）
 * 免费版、支持上下文、学生作业专用
 */
@Component
@RequiredArgsConstructor
public class DeepSeekAiUtil {

    private final DeepSeekAiProperties deepSeekAiProperties;

    /**
     * 发送AI请求，返回AI回答
     *
     * @param context 拼接好的上下文
     * @return AI真实回答
     */
    public String chat(String context) {
        String model = deepSeekAiProperties.getModel() != null
                ? deepSeekAiProperties.getModel()
                : "deepseek-chat";
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", context)
                )
        );

        try (HttpResponse response = HttpUtil.createPost(deepSeekAiProperties.getApiUrl())
                .header("Authorization", "Bearer " + deepSeekAiProperties.getApiKey())
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(body))
                .execute()) {
            String result = response.body();
            if (!response.isOk()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        describeHttpFailure(response.getStatus(), result));
            }
            if (StrUtil.isBlank(result)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "DeepSeek 返回体为空");
            }
            JSONObject root;
            try {
                root = JSONUtil.parseObj(result);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "DeepSeek 返回非 JSON：" + StrUtil.subPre(result, 500));
            }
            if (root.containsKey("error")) {
                JSONObject err = root.getJSONObject("error");
                String msg = err != null ? err.getStr("message", err.toString()) : root.getStr("error");
                if (containsIgnoreCase(msg, "insufficient balance")) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 服务账户余额不足，请到 DeepSeek 开放平台充值后再试");
                }
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "DeepSeek：" + msg);
            }
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "DeepSeek 响应缺少 choices：" + StrUtil.subPre(result, 500));
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            if (message == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "DeepSeek 响应缺少 message：" + StrUtil.subPre(result, 500));
            }
            String content = message.getStr("content");
            if (StrUtil.isBlank(content)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "DeepSeek 未返回正文内容");
            }
            return content;
        }
    }

    /**
     * 将 HTTP 错误与 DeepSeek 返回体整理为面向用户的说明（含常见状态码含义）。
     */
    private static String describeHttpFailure(int status, String body) {
        String apiMsg = extractDeepSeekErrorMessage(body);
        if (status == 402 || containsIgnoreCase(apiMsg, "insufficient balance")) {
            return "AI 服务账户余额不足，请到 DeepSeek 开放平台充值后再试";
        }
        if (status == 401) {
            return "AI 服务密钥无效或未授权，请检查配置中的 deepseek.api-key";
        }
        if (status == 429 || containsIgnoreCase(apiMsg, "rate limit")) {
            return "AI 服务请求过于频繁，请稍后再试";
        }
        String suffix = StrUtil.isNotBlank(apiMsg) ? apiMsg : StrUtil.subPre(body, 500);
        return "DeepSeek 请求失败 HTTP " + status + (StrUtil.isNotBlank(suffix) ? "：" + suffix : "");
    }

    private static String extractDeepSeekErrorMessage(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        try {
            JSONObject o = JSONUtil.parseObj(body);
            JSONObject err = o.getJSONObject("error");
            if (err != null) {
                return err.getStr("message");
            }
        } catch (Exception ignored) {
            // 非 JSON 时忽略，由调用方展示原文片段
        }
        return null;
    }

    private static boolean containsIgnoreCase(String s, String fragment) {
        return s != null && fragment != null && s.toLowerCase().contains(fragment.toLowerCase());
    }
}
