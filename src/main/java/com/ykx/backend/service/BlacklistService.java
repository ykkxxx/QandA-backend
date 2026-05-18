package com.ykx.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ykx.backend.model.entity.AccessBlacklist;

/**
 * 黑名单通用业务接口
 * 提供：用户ID/IP/用户名 的拉黑、解除拉黑、校验是否拉黑、分页查询功能
 * 供登录、注册、权限校验等核心流程调用
 */
public interface BlacklistService {

    /**
     * 拉黑指定用户ID（会导致该用户无法登录）
     * @param userId 要拉黑的用户ID
     * @param reason 拉黑原因（用于记录）
     */
    void addUserIdBlock(String userId, String reason);

    /**
     * 解除指定用户ID的拉黑
     * @param userId 要解除拉黑的用户ID
     */
    void removeUserIdBlock(String userId);

    /**
     * 拉黑指定IP（该IP无法注册、登录）
     * @param ip 要拉黑的IP地址
     * @param reason 拉黑原因
     */
    void addIpBlock(String ip, String reason);

    /**
     * 解除指定IP的拉黑
     * @param ip 要解除拉黑的IP
     */
    void removeIpBlock(String ip);


    /**
     * 判断用户ID是否被拉黑
     * @param userId 用户ID
     * @return true=拉黑，false=未拉黑
     */
    boolean isBlockedUserId(String userId);

    /**
     * 判断IP是否被拉黑
     * @param ip IP地址
     * @return true=拉黑，false=未拉黑
     */
    boolean isBlockedIp(String ip);


    /**
     * 分页查询所有黑名单记录
     * @param current 当前页码
     * @param size 每页条数
     * @return 分页后的黑名单列表
     */
    Page<AccessBlacklist> pageBlacklist(long current, long size);
}
