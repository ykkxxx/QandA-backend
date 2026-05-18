package com.ykx.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ykx.backend.model.entity.AccessBlacklist;

/**
 * 安全管理模块（管理员专用）
 * 包含：用户封禁、IP拉黑、用户名拉黑、黑名单查询
 */
public interface SecurityAdminService {

    /**
     * 管理员：封禁某个用户
     * @param userId 要封禁的用户ID
     * @param reason 封禁原因（记录日志/后台显示）
     */
    void banUser(String userId, String reason);

    /**
     * 管理员：解封某个用户
     * @param userId 要解封的用户ID
     */
    void unbanUser(String userId);

    /**
     * 管理员：拉黑IP（禁止该IP登录/注册）
     * @param ip 要拉黑的IP地址
     * @param reason 拉黑原因
     */
    void blockIp(String ip, String reason);

    /**
     * 管理员：解除IP拉黑
     * @param ip 要解除的IP地址
     */
    void unblockIp(String ip);


    /**
     * 管理员：分页查询所有黑名单记录（用户+IP+用户名）
     * @param page 页码
     * @param size 每页条数
     * @return 分页黑名单列表
     */
    Page<AccessBlacklist> listBlacklist(long page, long size);
}
