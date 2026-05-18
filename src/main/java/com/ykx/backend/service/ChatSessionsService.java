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
/**
 * 聊天会话业务接口
 * 功能：负责用户聊天会话的创建、管理、查询、删除等操作
 * 所有接口均需要用户已登录
 *
 * @author 13797
 */
public interface ChatSessionsService extends IService<ChatSessions> {

        /**
         * 创建新的聊天会话
         * 用户每次开启新对话时调用，会生成一个新的 session_id
         * @return 会话基本信息 VO
         */
        BaseResponse<ChatSessionVO> createSession();

        /**
         * 更新会话标题（重命名）
         * @param session_id 会话ID
         * @param title 新的会话标题
         * @return 是否更新成功
         */
        BaseResponse<Boolean> updateSessionTitle(String session_id, String title);

        /**
         * 清空当前登录用户的所有聊天会话
         * 谨慎操作：会删除该用户所有会话及关联消息
         * @return 是否清空成功
         */
        BaseResponse<Boolean> clearAllSessions();

        /**
         * 根据会话ID，查询该会话的完整历史记录（含所有消息）
         * @param session_id 会话ID
         * @return 会话完整历史信息（含标题、创建时间、消息列表等）
         */
        BaseResponse<ChatSessionHistoryVO> getTotalHistoryInfo(String session_id);

        /**
         * 删除指定的聊天会话（会级联删除该会话下的所有消息）
         * @param session_id 要删除的会话ID
         * @return 无返回数据
         */
        BaseResponse<Void> deleteSession(String session_id);

        /**
         * 获取当前登录用户的所有聊天会话列表
         * @return 会话列表（只展示基本信息，不含详细消息）
         */
        BaseResponse<List<ChatSessionVO>> getMySessionList();
}
