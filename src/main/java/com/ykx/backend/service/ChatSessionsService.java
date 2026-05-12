package com.ykx.backend.service;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.model.entity.ChatSessionHistoryVO;
import com.ykx.backend.model.entity.ChatSessions;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ykx.backend.model.vo.session.ChatSessionVO;

import java.util.List;

/**
* @author 13797
* @description 针对表【chat_sessions】的数据库操作Service
* @createDate 2026-05-11 19:53:51
*/
public interface ChatSessionsService extends IService<ChatSessions> {
        /**
         * 创建新会话（用户开始新聊天 → 必须有）
         */
        BaseResponse<ChatSessionVO> createSession();
        /**
         * 更新会话标题（重命名会话）
         */

        BaseResponse<Boolean> updateSessionTitle(String session_id, String title);
        /**
         * 清空当前用户所有会话（一键清空）
         */
        BaseResponse<Boolean> clearAllSessions();
        //根据会话id 查询该会话下的所有历史消息
        BaseResponse<ChatSessionHistoryVO> getTotalHistoryInfo(String session_id);
        //删除指定会话
        BaseResponse<Void> deleteSession(String session_id);
        //获取当前登录用户的所有会话列表
        BaseResponse<List<ChatSessionVO>>getMySessionList();

}
