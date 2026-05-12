package com.ykx.backend.controller;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.model.dto.MessageSendRequestDTO;
import com.ykx.backend.model.entity.ChatMessages;
import com.ykx.backend.model.vo.session.MessageSendResponseVO;
import com.ykx.backend.service.ChatMessagesService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/message")
public class MessageController {

    @Resource
    private ChatMessagesService chatMessagesService;

    /**
     * 1、发送消息接口（核心接口）
     * 包含：上下文拼接、事务双写、自动创建会话、自动修改标题
     */
    @PostMapping("/send")
    public BaseResponse<MessageSendResponseVO> sendMessage(@RequestBody MessageSendRequestDTO messageSendRequestDTO){
        return chatMessagesService.sendMessage(messageSendRequestDTO);
    }

    /**
     * 2、获取会话消息列表
     * 用于前端打开会话加载历史聊天记录
     */
    @GetMapping("/list/{session_id}")
    public BaseResponse<List<ChatMessages>> getMessageList(@PathVariable String session_id){
        return chatMessagesService.getMessageList(session_id);
    }
}

