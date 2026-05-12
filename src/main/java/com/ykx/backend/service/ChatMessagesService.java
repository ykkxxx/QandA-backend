package com.ykx.backend.service;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.model.dto.MessageSendRequestDTO;
import com.ykx.backend.model.entity.ChatMessages;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ykx.backend.model.vo.session.MessageSendResponseVO;

import java.util.List;

/**
* @author 13797
* @description 针对表【chat_messages】的数据库操作Service
* @createDate 2026-05-11 19:54:53
*/
public interface ChatMessagesService extends IService<ChatMessages> {
        //发送信息
        BaseResponse<MessageSendResponseVO> sendMessage(MessageSendRequestDTO messageSendRequestDTO);
        //获取当前会话中的历史信息
        BaseResponse<List<ChatMessages>> getMessageList(String sessionId);
}
