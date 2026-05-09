package com.ykx.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.mapper.AccessBlacklistMapper;
import com.ykx.backend.model.entity.AccessBlacklist;
import com.ykx.backend.model.enums.BlacklistBlockType;
import com.ykx.backend.service.BlacklistService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
public class BlacklistServiceImpl extends ServiceImpl<AccessBlacklistMapper, AccessBlacklist>
        implements BlacklistService {

    private static final String REDIS_USER = "access:bl:user:";
    private static final String REDIS_IP = "access:bl:ip:";
    private static final String REDIS_USERNAME = "access:bl:username:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUserIdBlock(String userId, String reason) {
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }
        upsertBlock(BlacklistBlockType.USER_ID.getCode(), userId.trim(), reason);
        try {
            stringRedisTemplate.opsForValue().set(REDIS_USER + userId.trim(), "1");
        } catch (Exception e) {
            log.warn("Redis 写入用户黑名单失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缓存服务不可用");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserIdBlock(String userId) {
        if (StrUtil.isBlank(userId)) {
            return;
        }
        removeDb(BlacklistBlockType.USER_ID.getCode(), userId.trim());
        try {
            stringRedisTemplate.delete(REDIS_USER + userId.trim());
        } catch (Exception e) {
            log.warn("Redis 删除用户黑名单失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addIpBlock(String ip, String reason) {
        if (StrUtil.isBlank(ip)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "IP 不能为空");
        }
        String v = ip.trim();
        upsertBlock(BlacklistBlockType.IP.getCode(), v, reason);
        try {
            stringRedisTemplate.opsForValue().set(REDIS_IP + v, "1");
        } catch (Exception e) {
            log.warn("Redis 写入 IP 黑名单失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缓存服务不可用");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeIpBlock(String ip) {
        if (StrUtil.isBlank(ip)) {
            return;
        }
        String v = ip.trim();
        removeDb(BlacklistBlockType.IP.getCode(), v);
        try {
            stringRedisTemplate.delete(REDIS_IP + v);
        } catch (Exception e) {
            log.warn("Redis 删除 IP 黑名单失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUsernameBlock(String username, String reason) {
        if (StrUtil.isBlank(username)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名不能为空");
        }
        String v = username.trim();
        upsertBlock(BlacklistBlockType.USERNAME.getCode(), v, reason);
        try {
            stringRedisTemplate.opsForValue().set(REDIS_USERNAME + v, "1");
        } catch (Exception e) {
            log.warn("Redis 写入用户名黑名单失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缓存服务不可用");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUsernameBlock(String username) {
        if (StrUtil.isBlank(username)) {
            return;
        }
        String v = username.trim();
        removeDb(BlacklistBlockType.USERNAME.getCode(), v);
        try {
            stringRedisTemplate.delete(REDIS_USERNAME + v);
        } catch (Exception e) {
            log.warn("Redis 删除用户名黑名单失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean isBlockedUserId(String userId) {
        if (StrUtil.isBlank(userId)) {
            return false;
        }
        String key = REDIS_USER + userId.trim();
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis 查询用户黑名单失败，降级查库: {}", e.getMessage());
        }
        return existsInDb(BlacklistBlockType.USER_ID.getCode(), userId.trim());
    }

    @Override
    public boolean isBlockedIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        String key = REDIS_IP + ip.trim();
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis 查询 IP 黑名单失败，降级查库: {}", e.getMessage());
        }
        return existsInDb(BlacklistBlockType.IP.getCode(), ip.trim());
    }

    @Override
    public boolean isBlockedUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return false;
        }
        String key = REDIS_USERNAME + username.trim();
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis 查询用户名黑名单失败，降级查库: {}", e.getMessage());
        }
        return existsInDb(BlacklistBlockType.USERNAME.getCode(), username.trim());
    }

    @Override
    public Page<AccessBlacklist> pageBlacklist(long current, long size) {
        Page<AccessBlacklist> p = new Page<>(current, size);
        return page(p, new LambdaQueryWrapper<AccessBlacklist>().orderByDesc(AccessBlacklist::getCreated_at));
    }

    private void upsertBlock(String type, String value, String reason) {
        removeDb(type, value);
        AccessBlacklist row = new AccessBlacklist();
        row.setBlock_type(type);
        row.setBlock_value(value);
        row.setReason(StrUtil.blankToDefault(reason, ""));
        row.setCreated_at(new Date());
        save(row);
    }

    private void removeDb(String type, String value) {
        remove(new LambdaQueryWrapper<AccessBlacklist>()
                .eq(AccessBlacklist::getBlock_type, type)
                .eq(AccessBlacklist::getBlock_value, value));
    }

    private boolean existsInDb(String type, String value) {
        return count(new LambdaQueryWrapper<AccessBlacklist>()
                .eq(AccessBlacklist::getBlock_type, type)
                .eq(AccessBlacklist::getBlock_value, value)) > 0;
    }
}
