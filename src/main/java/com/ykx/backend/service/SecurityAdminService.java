package com.ykx.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ykx.backend.model.entity.AccessBlacklist;

public interface SecurityAdminService {

    void banUser(String userId, String reason);

    void unbanUser(String userId);

    void blockIp(String ip, String reason);

    void unblockIp(String ip);

    void blockUsername(String username, String reason);

    void unblockUsername(String username);

    Page<AccessBlacklist> listBlacklist(long page, long size);
}
