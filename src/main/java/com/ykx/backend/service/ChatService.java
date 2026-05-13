package com.ykx.backend.service;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.model.dto.MessageSendRequestDTO;
import com.ykx.backend.model.vo.session.MessageSendResponseVO;

/**
 * 聊天总编排：会话校验、RAG 预检索、LangChain4j Agent 调用、消息持久化。
 */
public interface ChatService {

    BaseResponse<MessageSendResponseVO> sendMessage(MessageSendRequestDTO dto);
}
