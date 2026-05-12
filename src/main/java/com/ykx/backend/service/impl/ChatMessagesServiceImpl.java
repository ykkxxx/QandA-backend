package com.ykx.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ykx.backend.ai.DeepSeekAiUtil;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.mapper.ChatSessionsMapper;
import com.ykx.backend.model.dto.MessageSendRequestDTO;
import com.ykx.backend.model.entity.ChatMessages;
import com.ykx.backend.model.entity.ChatSessions;
import com.ykx.backend.model.vo.session.MessageSendResponseVO;
import com.ykx.backend.service.ChatMessagesService;
import com.ykx.backend.mapper.ChatMessagesMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
* @author 13797
* @description 针对表【chat_messages】的数据库操作Service实现
* @createDate 2026-05-11 19:54:53
*/
@Service
public class ChatMessagesServiceImpl extends ServiceImpl<ChatMessagesMapper, ChatMessages>
    implements ChatMessagesService{
    @Resource
    private ChatSessionsMapper chatSessionsMapper;

    @Resource
    private ChatMessagesMapper chatMessagesMapper;

    @Resource
    private DeepSeekAiUtil deepSeekAiUtil;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<MessageSendResponseVO> sendMessage(MessageSendRequestDTO messageSendRequestDTO) {
        // 1. 获取当前登录用户（禁止前端传用户id）
        String userId = UserContext.getUserId();
        String session_id = messageSendRequestDTO.getSessionId();
        String query = messageSendRequestDTO.getQuery();
        // 2. 入参校验
        if (StrUtil.isBlank(query)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提问内容不能为空");
        }
        // 3. 会话判定：为空则自动创建会话，不为空则校验归属权
        ChatSessions chatSession;
        if (StrUtil.isBlank(session_id)) {
            // 新建会话
            chatSession = new ChatSessions();
            chatSession.setId(UUID.randomUUID().toString());
            chatSession.setUser_id(userId);
            chatSession.setTitle("新对话");
            chatSessionsMapper.insert(chatSession);
            session_id = chatSession.getId();
        } else {
            // 查询会话并校验是否属于本人
            chatSession = chatSessionsMapper.selectById(session_id);
            if (chatSession == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
            }
            if (!chatSession.getUser_id().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权访问他人会话");
            }
        }
        // 4. 上下文拼接：查询该会话所有历史消息（时间正序）
        LambdaQueryWrapper<ChatMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessages::getSession_id, session_id);
        wrapper.orderByAsc(ChatMessages::getCreated_at);
        List<ChatMessages> historyList = chatMessagesMapper.selectList(wrapper);
        //构建上下文
        StringBuilder context = new StringBuilder();
        for (ChatMessages m : historyList) {
            if("user".equals(m.getRole())){
                context.append("用户：").append(m.getContent()).append("\n");
            }else{
                context.append("AI：").append(m.getContent()).append("\n");
            }
        }
        // 追加当前用户提问
        context.append("用户：").append(query);
        // 模拟AI返回（后期替换真实AI接口）
        String aiAnswer = deepSeekAiUtil.chat(context.toString());
        // 5. 消息双写：保存用户消息 + AI消息
        ChatMessages userMessage = new ChatMessages();
        userMessage.setId(UUID.randomUUID().toString());
        userMessage.setSession_id(session_id);
        userMessage.setUser_id(userId);
        userMessage.setRole("user");
        userMessage.setContent(query);
        userMessage.setCreated_at(LocalDateTime.now());

        ChatMessages aiMessage = new ChatMessages();
        aiMessage.setId(UUID.randomUUID().toString());
        aiMessage.setSession_id(session_id);
        aiMessage.setUser_id(userId);
        aiMessage.setRole("assistant");
        aiMessage.setContent(aiAnswer);
        aiMessage.setCreated_at(LocalDateTime.now());
        this.saveBatch(List.of(userMessage, aiMessage));
        // 6. 首次对话自动修改会话标题
        if ("新对话".equals(chatSession.getTitle())) {
            //截取用户问题的前15个字当标题
            String newTitle = query.length() > 15 ? query.substring(0, 15) : query;
            chatSession.setTitle(newTitle);
            chatSessionsMapper.updateById(chatSession);
        }
        // 7. 组装返回VO
        MessageSendResponseVO vo = new MessageSendResponseVO();
        vo.setSessionId(session_id);
        vo.setContent(aiAnswer);
        return ResultUtils.success(vo);
    }

    @Override
    public BaseResponse<List<ChatMessages>> getMessageList(String sessionId) {
        // 1、获取当前登录用户id
        String userId = UserContext.getUserId();
        // 2、校验sessionId是否为空
        if (StrUtil.isBlank(sessionId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"会话id不能为空");
        }
        // 3、校验会话是否存在
        ChatSessions chatSessions = chatSessionsMapper.selectById(sessionId);
        if (chatSessions == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"会话不存在");
        }
        // 4、权限校验：只能查看自己的会话
        if (!chatSessions.getUser_id().equals(userId)){
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR,"无权查看他人会话消息");
        }
        // 5、根据会话id查询所有消息，时间正序（保证聊天顺序不乱）
        LambdaQueryWrapper<ChatMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessages::getSession_id,sessionId);
        wrapper.orderByAsc(ChatMessages::getCreated_at);
        List<ChatMessages> messageList = chatMessagesMapper.selectList(wrapper);
        // 6、返回消息列表
        return ResultUtils.success(messageList);
    }
}




