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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    //它同时操作了【数据库 + Redis】，必须保证要么都成功，要么都失败！
    //登录是查询操作，不修改数据，不需要事务！
    //新增一条记录 + 添加redis状态
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
    // 方法作用：传入一个IP，判断它是否被拉黑（封禁）
    public boolean isBlockedIp(String ip) {

        // 1. 如果IP是空/空白，直接不算拉黑
        if (StrUtil.isBlank(ip)) {
            return false;
        }

        // 2. 拼接Redis的key，格式：access:bl:ip:192.168.1.1
        String key = REDIS_IP + ip.trim();

        try {
            // 3. 先查 Redis：如果这个IP在Redis里存在 → 说明是黑名单，返回true
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return true;
            }

            // 4. 如果Redis挂了/连不上 → 捕获异常，不影响主流程
        } catch (Exception e) {
            log.warn("Redis 查询 IP 黑名单失败，降级查库: {}", e.getMessage());
        }

        // 5. Redis里没查到，或者Redis挂了 → 去数据库查是否在黑名单
        return existsInDb(BlacklistBlockType.IP.getCode(), ip.trim());
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
