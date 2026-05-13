package com.ykx.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.mapper.ChatMessagesMapper;
import com.ykx.backend.mapper.ChatSessionsMapper;
import com.ykx.backend.model.dto.MessageSendRequestDTO;
import com.ykx.backend.model.entity.ChatMessages;
import com.ykx.backend.model.entity.ChatSessions;
import com.ykx.backend.model.vo.session.MessageSendResponseVO;
import com.ykx.backend.service.ChatMessagesService;
import com.ykx.backend.service.ChatService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息持久化与列表查询；发送消息编排委托 {@link ChatService}。
 */
@Service
public class ChatMessagesServiceImpl extends ServiceImpl<ChatMessagesMapper, ChatMessages>
        implements ChatMessagesService {

    @Resource
    private ChatSessionsMapper chatSessionsMapper;

    @Resource
    private ChatMessagesMapper chatMessagesMapper;

    @Resource
    private ChatService chatService;

    @Override
    public BaseResponse<MessageSendResponseVO> sendMessage(MessageSendRequestDTO messageSendRequestDTO) {
        return chatService.sendMessage(messageSendRequestDTO);
    }

    @Override
    public BaseResponse<List<ChatMessages>> getMessageList(String sessionId) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(sessionId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话id不能为空");
        }
        ChatSessions chatSessions = chatSessionsMapper.selectById(sessionId);
        if (chatSessions == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        if (!chatSessions.getUser_id().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权查看他人会话消息");
        }
        LambdaQueryWrapper<ChatMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessages::getSession_id, sessionId);
        wrapper.orderByAsc(ChatMessages::getCreated_at);
        List<ChatMessages> messageList = chatMessagesMapper.selectList(wrapper);
        return ResultUtils.success(messageList);
    }
}
