package com.ykx.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ykx.backend.ai.lc.ChatAssistant;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.config.AgentProperties;
import com.ykx.backend.config.DeepSeekAiProperties;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.mapper.ChatMessagesMapper;
import com.ykx.backend.mapper.ChatSessionsMapper;
import com.ykx.backend.model.dto.MessageSendRequestDTO;
import com.ykx.backend.model.entity.ChatMessages;
import com.ykx.backend.model.entity.ChatSessions;
import com.ykx.backend.model.vo.rag.RagContextVO;
import com.ykx.backend.model.vo.session.MessageSendResponseVO;
import com.ykx.backend.service.ChatService;
import com.ykx.backend.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.ykx.backend.agent.AgentToolCallContext;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionsMapper chatSessionsMapper;
    private final ChatMessagesMapper chatMessagesMapper;
    private final RagService ragService;
    private final ChatAssistant chatAssistant;
    private final DeepSeekAiProperties deepSeekAiProperties;
    private final AgentProperties agentProperties;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<MessageSendResponseVO> sendMessage(MessageSendRequestDTO dto) {
        String userId = UserContext.getUserId();
        String sessionId = dto.getSessionId();
        String query = dto.getQuery();

        if (StrUtil.isBlank(query)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提问内容不能为空");
        }
        if (!StringUtils.hasText(deepSeekAiProperties.getApiKey())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未配置大模型密钥：请在配置中设置 deepseek.api-key");
        }

        ChatSessions chatSession;
        if (StrUtil.isBlank(sessionId)) {
            chatSession = new ChatSessions();
            chatSession.setId(UUID.randomUUID().toString());
            chatSession.setUser_id(userId);
            chatSession.setTitle("新对话");
            chatSessionsMapper.insert(chatSession);
            sessionId = chatSession.getId();
        } else {
            chatSession = chatSessionsMapper.selectById(sessionId);
            if (chatSession == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
            }
            if (!chatSession.getUser_id().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权访问他人会话");
            }
        }


        LambdaQueryWrapper<ChatMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessages::getSession_id, sessionId);
        wrapper.orderByDesc(ChatMessages::getCreated_at);
        wrapper.last("limit " + Math.max(1, agentProperties.getMaxHistoryMessages()));
        List<ChatMessages> historyList = chatMessagesMapper.selectList(wrapper);
        Collections.reverse(historyList);
        //将list按时间降序 按role 拆解成user 和assistant
        String historyBlock = buildHistoryBlock(historyList);
        String agentPayload = buildAgentPayload(historyBlock, query.trim(), userId);

        String aiAnswer;
        AgentToolCallContext.init(userId,sessionId);
        try {
            aiAnswer = chatAssistant.reply(agentPayload);
        } catch (Exception e) {
            log.error("大模型调用失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, friendlyLlmMessage(e));
        }finally {
            AgentToolCallContext.clear();
        }
        if (StrUtil.isBlank(aiAnswer)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "大模型未返回有效内容，请稍后重试");
        }

        ChatMessages userMessage = new ChatMessages();
        userMessage.setId(UUID.randomUUID().toString());
        userMessage.setSession_id(sessionId);
        userMessage.setUser_id(userId);
        userMessage.setRole("user");
        userMessage.setContent(query);
        userMessage.setCreated_at(LocalDateTime.now());

        ChatMessages aiMessage = new ChatMessages();
        aiMessage.setId(UUID.randomUUID().toString());
        aiMessage.setSession_id(sessionId);
        aiMessage.setUser_id(userId);
        aiMessage.setRole("assistant");
        aiMessage.setContent(aiAnswer);
        aiMessage.setCreated_at(LocalDateTime.now());

        chatMessagesMapper.insert(userMessage);
        chatMessagesMapper.insert(aiMessage);

        if ("新对话".equals(chatSession.getTitle())) {
            String newTitle = query.length() > 15 ? query.substring(0, 15) : query;
            chatSession.setTitle(newTitle);
            chatSessionsMapper.updateById(chatSession);
        }

        MessageSendResponseVO vo = new MessageSendResponseVO();
        vo.setSessionId(sessionId);
        vo.setContent(aiAnswer);
        return ResultUtils.success(vo);
    }
    ///**
    // * 把聊天消息列表，拼接成一段连续的文本历史
    // * 格式：
    // * 用户：xxx
    // * 助手：xxx
    // * 用户：xxx
    // * 助手：xxx
    // *
    // * 主要用来给大模型提供对话上下文
    // */
    private static String buildHistoryBlock(List<ChatMessages> historyList) {
        if (historyList == null || historyList.isEmpty()) {
            return "（暂无历史消息）";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessages m : historyList) {
            if ("user".equals(m.getRole())) {
                sb.append("用户：").append(m.getContent()).append("\n");
            } else {
                sb.append("助手：").append(m.getContent()).append("\n");
            }
        }
        return sb.toString().trim();
    }
    //把【历史对话 + 检索到的知识库 + 用户当前问题】拼成一段完整的提示词，发给 AI 大模型！
    private String buildAgentPayload(String historyBlock, String query, String userId) {
        String ragBlock;
        try {
            RagContextVO rag = ragService.buildContext(userId, query);
            if (StringUtils.hasText(rag.getContextText())) {
                ragBlock = rag.getContextText();
            } else {
                ragBlock = "（预检索：知识库暂无匹配片段。若问题依赖个人资料，可调用工具 searchUserKnowledge 补充；否则请诚实说明依据不足，勿编造。）";
            }
        } catch (Exception e) {
            log.warn("RAG 预检索失败: {}", e.getMessage());
            ragBlock = "（预检索不可用：" + summarizeException(e)
                    + "。请结合对话历史作答；勿假装已检索到知识库内容。）";
        }

        return """
                【对话历史】
                %s

                【预检索知识库】
                %s

                【当前用户问题】
                %s
                """.formatted(historyBlock, ragBlock, query);
    }

    private static String summarizeException(Exception e) {
        String m = e.getMessage();
        return StrUtil.isBlank(m) ? "未知原因" : StrUtil.subPre(m, 200);
    }

    private static String friendlyLlmMessage(Throwable e) {
        String raw = e.getMessage() != null ? e.getMessage() : "";
        String lower = raw.toLowerCase();
        if (raw.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key")) {
            return "大模型服务鉴权失败：请检查 deepseek.api-key 是否有效";
        }
        if (raw.contains("402") || lower.contains("insufficient balance") || lower.contains("余额")) {
            return "大模型账户余额不足或欠费，请前往服务商控制台充值后再试";
        }
        if (raw.contains("429") || lower.contains("rate limit")) {
            return "大模型请求过于频繁，请稍后再试";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "大模型响应超时，请稍后重试或缩短问题长度";
        }
        return "大模型暂时不可用：" + StrUtil.subPre(raw, 300);
    }
}
