package com.ykx.backend.controller;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.model.entity.ChatSessionHistoryVO;
import com.ykx.backend.model.vo.session.ChatSessionVO;
import com.ykx.backend.service.ChatSessionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final ChatSessionsService chatSessionService;
    /**
     * 1. 创建新会话
     * POST /api/session
     */
    @PostMapping
    public BaseResponse<ChatSessionVO> createSession() {
        return chatSessionService.createSession();
    }
    /**
     * 2. 修改会话标题
     * PUT /api/session/{sessionId}/title
     */
    @PutMapping("/{sessionId}/title")
    public BaseResponse<Boolean> updateSessionTitle(
            @PathVariable String sessionId,
            @RequestParam String title
    ) {
        return chatSessionService.updateSessionTitle(sessionId, title);
    }

    /**
     * 3. 清空当前用户所有会话
     * DELETE /api/session/clear
     */
    @DeleteMapping("/clear")
    public BaseResponse<Boolean> clearAllSessions() {
        return chatSessionService.clearAllSessions();
    }

    /**
     * 获取当前登录用户的所有会话列表
     * GET /api/sessions
     */
    @GetMapping("/sessions")
    public BaseResponse<List<ChatSessionVO>> getMySessions() {
        return chatSessionService.getMySessionList();
    }

    /**
     * 获取指定会话的完整聊天历史
     * GET /api/session/{sessionId}
     */
    @GetMapping("/{session_id}")
    public BaseResponse<ChatSessionHistoryVO> getSessionHistory(@PathVariable String session_id) {
        return chatSessionService.getTotalHistoryInfo(session_id);
    }

    /**
     * 删除指定聊天会话
     * DELETE /api/session/{sessionId}
     */
    @DeleteMapping("/{session_id}")
    public BaseResponse<Void> deleteSession(@PathVariable String session_id) {
        return chatSessionService.deleteSession(session_id);
    }

}
