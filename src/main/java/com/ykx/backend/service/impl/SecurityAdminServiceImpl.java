package com.ykx.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ykx.backend.common.UserStatusConstants;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.entity.AccessBlacklist;
import com.ykx.backend.model.entity.Users;
import com.ykx.backend.service.BlacklistService;
import com.ykx.backend.service.SecurityAdminService;
import com.ykx.backend.service.UsersService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAdminServiceImpl implements SecurityAdminService {

    @Resource
    private UsersService usersService;
    @Resource
    private BlacklistService blacklistService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void banUser(String userId, String reason) {
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }
        Users user = usersService.getById(userId.trim());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        user.setStatus(UserStatusConstants.BANNED);
        if (!usersService.updateById(user)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "封禁失败");
        }
        usersService.evictUserSession(userId.trim());
        blacklistService.addUserIdBlock(userId.trim(), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbanUser(String userId) {
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }
        Users user = usersService.getById(userId.trim());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        user.setStatus(UserStatusConstants.NORMAL);
        if (!usersService.updateById(user)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解封失败");
        }
        blacklistService.removeUserIdBlock(userId.trim());
    }

    @Override
    public void blockIp(String ip, String reason) {
        blacklistService.addIpBlock(ip, reason);
    }

    @Override
    public void unblockIp(String ip) {
        blacklistService.removeIpBlock(ip);
    }

    @Override
    public void blockUsername(String username, String reason) {
        blacklistService.addUsernameBlock(username, reason);
    }

    @Override
    public void unblockUsername(String username) {
        blacklistService.removeUsernameBlock(username);
    }

    @Override
    public Page<AccessBlacklist> listBlacklist(long page, long size) {
        return blacklistService.pageBlacklist(page, size);
    }
}
