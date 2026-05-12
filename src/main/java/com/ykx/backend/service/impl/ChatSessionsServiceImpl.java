package com.ykx.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.mapper.ChatMessagesMapper;
import com.ykx.backend.model.entity.ChatMessageVO;
import com.ykx.backend.model.entity.ChatMessages;
import com.ykx.backend.model.entity.ChatSessionHistoryVO;
import com.ykx.backend.model.entity.ChatSessions;
import com.ykx.backend.model.vo.session.ChatSessionVO;
import com.ykx.backend.service.ChatSessionsService;
import com.ykx.backend.mapper.ChatSessionsMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
* @author 13797
* @description 针对表【chat_sessions】的数据库操作Service实现
* @createDate 2026-05-11 19:53:51
*/
@Service
public class ChatSessionsServiceImpl extends ServiceImpl<ChatSessionsMapper, ChatSessions>
    implements ChatSessionsService{
    @Resource
    private ChatSessionsMapper chatSessionsMapper;

    @Resource
    private ChatMessagesMapper chatMessagesMapper;

    @Override
    public BaseResponse<ChatSessionVO> createSession() {
        // 1. 获取当前登录用户
        String userId = UserContext.getUserId();
        // 2. 新建会话对象
        ChatSessions session = new ChatSessions();
        String sessionId = UUID.randomUUID().toString();
        session.setId(sessionId);
        // 设置当前用户
        session.setUser_id(userId);
        // 默认标题：新对话
        session.setTitle("新对话");
        // 3. 插入数据库
        boolean saved = this.save(session);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建会话失败");
        }
        // 4. 封装 VO 返回
        ChatSessionVO vo = new ChatSessionVO();
        vo.setSessionId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setCreatedAt(session.getCreated_at());
        vo.setUpdatedAt(session.getUpdated_at());

        return ResultUtils.success(vo);

    }

    @Override
    public BaseResponse<Boolean> updateSessionTitle(String session_id, String title) {
        // 1. 基础校验
        if (StrUtil.isBlank(session_id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        if (StrUtil.isBlank(title)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题不能为空");
        }
        // 可选：限制标题长度
        if (title.length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题长度不能超过50");
        }
        // 2. 获取当前登录用户
        String currentUserId = UserContext.getUserId();

        // 3. 查询会话是否存在
        ChatSessions session = this.getById(session_id);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        // 4. 权限校验：只能改自己的会话
        if (!currentUserId.equals(session.getUser_id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限修改他人会话");
        }
        // 5. 更新标题
        session.setTitle(title.trim());
        boolean updated = this.updateById(session);

        return ResultUtils.success(updated);
    }

    @Override
    public BaseResponse<Boolean> clearAllSessions() {
        // 1. 获取当前登录用户ID
        String currentUserId = UserContext.getUserId();
        // 2. 条件：只删除当前用户的所有会话
        LambdaQueryWrapper<ChatSessions> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessions::getUser_id, currentUserId);
        boolean removed = this.remove(wrapper);
        return ResultUtils.success(removed);
    }

    @Override
    public BaseResponse<ChatSessionHistoryVO> getTotalHistoryInfo(String session_id) {
        // 1. 判断 sessionId 是否合法
        if (StrUtil.isBlank(session_id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        // 2. 从 UserContext 获取当前登录用户ID（拦截器已解析好）
        String currentUserId = UserContext.getUserId();
        System.out.println("userId:" + currentUserId );

        // 3. 查询会话是否存在
        ChatSessions session = chatSessionsMapper.selectById(session_id);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        // 4. 权限校验：只能查看自己的会话
        if (!currentUserId.equals(session.getUser_id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限访问他人会话");
        }
        // 5. 查询该会话下所有消息，按创建时间 升序 排列
        LambdaQueryWrapper<ChatMessages> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessages::getSession_id, session_id);
        queryWrapper.orderByAsc(ChatMessages::getCreated_at); // 时间正序，最早在前
        //WHERE session_id = ?
        //ORDER BY created_at ASC;
        List<ChatMessages> messageList = chatMessagesMapper.selectList(queryWrapper);
        // 6. 封装 VO
        ChatSessionHistoryVO sessionVO = new ChatSessionHistoryVO();
        sessionVO.setSessionId(session.getId());
        sessionVO.setUserId(session.getUser_id());
        sessionVO.setTitle(session.getTitle());
        sessionVO.setMetadata(session.getMetadata());
        sessionVO.setCreatedAt(session.getCreated_at());
        sessionVO.setUpdatedAt(session.getUpdated_at());
        // 消息转 VO
        List<ChatMessageVO> messageVOList = messageList.stream().map(msg -> {
            ChatMessageVO vo = new ChatMessageVO();
            vo.setId(msg.getId());
            vo.setRole(msg.getRole());
            vo.setContent(msg.getContent());
            vo.setCreatedAt(msg.getCreated_at());
            return vo;
        }).collect(Collectors.toList());
        sessionVO.setMessageList(messageVOList);

        // 7. 返回
        return ResultUtils.success(sessionVO);
    }

    @Override
    public BaseResponse<Void> deleteSession(String session_id) {
        // 1. 校验 sessionId 非空
        if (StrUtil.isBlank(session_id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        // 2. 获取当前登录用户ID
        String currentUserId = UserContext.getUserId();
        // 3. 查询会话是否存在
        ChatSessions chatSession = chatSessionsMapper.selectById(session_id);
        if (chatSession == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        // 4. 权限校验：只能删除自己的会话
        if (!currentUserId.equals(chatSession.getUser_id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限删除他人会话");
        }
        // 5. 删除会话
        // 数据库外键 ON DELETE CASCADE → 自动级联删除该会话下所有 chat_messages
        chatSessionsMapper.deleteById(session_id);
        return ResultUtils.success(null);
    }

    @Override
    public BaseResponse<List<ChatSessionVO>> getMySessionList() {
        // 1. 获取当前登录用户ID（拦截器已存入）
        String currentUserId = UserContext.getUserId();
        // 2. 查询该用户的所有会话，按最后更新时间倒序（最新的排最上面）
        LambdaQueryWrapper<ChatSessions> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatSessions::getUser_id, currentUserId);
        queryWrapper.orderByDesc(ChatSessions::getUpdated_at);
        List<ChatSessions> sessionList = chatSessionsMapper.selectList(queryWrapper);

        // 3. 转 VO
        List<ChatSessionVO> voList = sessionList.stream().map(session -> {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setSessionId(session.getId());
            vo.setTitle(session.getTitle());
            vo.setCreatedAt(session.getCreated_at());
            vo.setUpdatedAt(session.getUpdated_at());
            return vo;
        }).collect(Collectors.toList());
        return ResultUtils.success(voList);
    }
}




