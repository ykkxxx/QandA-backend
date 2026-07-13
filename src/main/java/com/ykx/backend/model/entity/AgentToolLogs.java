package com.ykx.backend.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * 智能工具调用日志表
 * @TableName agent_tool_logs
 */
@TableName(value ="agent_tool_logs")
@Data
public class AgentToolLogs {
    /**
     * 主键ID
     */
    @TableId
    private String id;

    /**
     * 用户ID
     */
    private String user_id;

    /**
     * 会话ID
     */
    private String session_id;

    /**
     * 工具名称
     */
    private String tool_name;

    /**
     * 工具入参
     */
    private String tool_input;

    /**
     * 工具返回结果
     */
    private String tool_output;

    /**
     * 是否执行成功 1成功 0失败
     */
    private Integer success;

    /**
     * 错误信息
     */
    private String error_message;

    /**
     * 耗时毫秒
     */
    private Long latency_ms;

    /**
     * 创建时间
     */
    private LocalDateTime created_at;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        AgentToolLogs other = (AgentToolLogs) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getUser_id() == null ? other.getUser_id() == null : this.getUser_id().equals(other.getUser_id()))
            && (this.getSession_id() == null ? other.getSession_id() == null : this.getSession_id().equals(other.getSession_id()))
            && (this.getTool_name() == null ? other.getTool_name() == null : this.getTool_name().equals(other.getTool_name()))
            && (this.getTool_input() == null ? other.getTool_input() == null : this.getTool_input().equals(other.getTool_input()))
            && (this.getTool_output() == null ? other.getTool_output() == null : this.getTool_output().equals(other.getTool_output()))
            && (this.getSuccess() == null ? other.getSuccess() == null : this.getSuccess().equals(other.getSuccess()))
            && (this.getError_message() == null ? other.getError_message() == null : this.getError_message().equals(other.getError_message()))
            && (this.getLatency_ms() == null ? other.getLatency_ms() == null : this.getLatency_ms().equals(other.getLatency_ms()))
            && (this.getCreated_at() == null ? other.getCreated_at() == null : this.getCreated_at().equals(other.getCreated_at()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getUser_id() == null) ? 0 : getUser_id().hashCode());
        result = prime * result + ((getSession_id() == null) ? 0 : getSession_id().hashCode());
        result = prime * result + ((getTool_name() == null) ? 0 : getTool_name().hashCode());
        result = prime * result + ((getTool_input() == null) ? 0 : getTool_input().hashCode());
        result = prime * result + ((getTool_output() == null) ? 0 : getTool_output().hashCode());
        result = prime * result + ((getSuccess() == null) ? 0 : getSuccess().hashCode());
        result = prime * result + ((getError_message() == null) ? 0 : getError_message().hashCode());
        result = prime * result + ((getLatency_ms() == null) ? 0 : getLatency_ms().hashCode());
        result = prime * result + ((getCreated_at() == null) ? 0 : getCreated_at().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", user_id=").append(user_id);
        sb.append(", session_id=").append(session_id);
        sb.append(", tool_name=").append(tool_name);
        sb.append(", tool_input=").append(tool_input);
        sb.append(", tool_output=").append(tool_output);
        sb.append(", success=").append(success);
        sb.append(", error_message=").append(error_message);
        sb.append(", latency_ms=").append(latency_ms);
        sb.append(", created_at=").append(created_at);
        sb.append("]");
        return sb.toString();
    }
}