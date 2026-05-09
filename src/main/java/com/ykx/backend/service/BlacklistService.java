package com.ykx.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ykx.backend.model.entity.AccessBlacklist;

public interface BlacklistService {

    void addUserIdBlock(String userId, String reason);

    void removeUserIdBlock(String userId);

    void addIpBlock(String ip, String reason);

    void removeIpBlock(String ip);

    void addUsernameBlock(String username, String reason);

    void removeUsernameBlock(String username);

    boolean isBlockedUserId(String userId);

    boolean isBlockedIp(String ip);

    boolean isBlockedUsername(String username);

    Page<AccessBlacklist> pageBlacklist(long current, long size);
}
