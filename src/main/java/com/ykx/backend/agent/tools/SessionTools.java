package com.ykx.backend.agent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.model.vo.session.ChatSessionVO;
import com.ykx.backend.service.ChatService;
import com.ykx.backend.service.ChatSessionsService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SessionTools {
    @Resource
    private ChatSessionsService chatSessionsService;
    @Tool("查看当前用户的所有聊天会话列表")
    public String listSessions(){
        try{
            BaseResponse<List<ChatSessionVO>> sessionObject = chatSessionsService.getMySessionList();
            List<ChatSessionVO> sessionList = sessionObject.getData();
            log.info("查询用户会话列表，数据sessionList: {}", sessionList);
            return JSONUtil.toJsonStr(sessionList);
        }catch (Exception e){
            log.error("查询用户会话列表异常", e);
            return "{\"code\":-1,\"msg\":\"查询会话列表失败\"}";
        }
    }
    /**
     * 删除指定的聊天会话
     * @param sessionId 会话唯一ID
     * @return 操作结果JSON字符串
     */
    @Tool("删除指定的聊天会话，sessionId为要删除的会话ID")
    public String deleteSession(String sessionId){
        if (StrUtil.isBlank(sessionId)) {
            log.warn("删除会话失败，sessionId参数为空");
            return JSONUtil.toJsonStr(ResultUtils.error(400, "会话ID不能为空"));
        }
        try {
            log.info("执行删除会话操作，sessionId={}", sessionId);
            BaseResponse<Void> response = chatSessionsService.deleteSession(sessionId);
            if (response.getCode() == 0) {
                log.info("会话删除成功，sessionId={}", sessionId);
                return JSONUtil.toJsonStr(ResultUtils.success("会话删除成功"));
            } else {
                log.warn("会话删除失败，sessionId={}", sessionId);
                return JSONUtil.toJsonStr(ResultUtils.error(500, response.getMessage()));
            }
        } catch (Exception e) {
            log.error("删除会话接口异常，sessionId={}", sessionId, e);
            return JSONUtil.toJsonStr(ResultUtils.error(500, "删除会话服务异常：" + e.getMessage()));
        }
    }
    @Tool("清空当前用户的所有聊天会话，此操作不可恢复")
    public String clearAllSessions(){
        try {
            log.info("执行清空当前用户全部聊天会话操作");
            BaseResponse<Boolean> response = chatSessionsService.clearAllSessions();
            if (response.getCode() == 0) {
                log.info("用户所有聊天会话清空完成，操作不可恢复");
                return JSONUtil.toJsonStr(ResultUtils.success("已清空当前用户全部聊天会话，该操作不可恢复"));
            } else {
                log.warn("清空会话失败");
                return JSONUtil.toJsonStr(ResultUtils.error(500, response.getMessage()));
            }
        } catch (Exception e) {
            log.error("清空全部会话服务异常", e);
            return JSONUtil.toJsonStr(ResultUtils.error(500, "清空会话失败，服务异常：" + e.getMessage()));
        }
    }
}
