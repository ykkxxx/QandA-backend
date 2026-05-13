package com.ykx.backend.chat;

import com.ykx.backend.config.ChatAppProperties;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Redisson 令牌桶限流：按用户 id 区分，适配高频提问。
 */
@Slf4j
@Component
public class ChatRateLimiter {

    private static final String CHAT_PREFIX = "rl:chat:send:";
    private static final String DOC_PREFIX = "rl:chat:doc:";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ChatAppProperties chatAppProperties;

    public void acquireSend(String userId) {
        acquire(userId, CHAT_PREFIX, Math.max(1, chatAppProperties.getRatePerMinute()), "发送过于频繁，请稍后再试");
    }

    public void acquireDocument(String userId) {
        acquire(userId, DOC_PREFIX, Math.max(1, chatAppProperties.getDocumentRatePerMinute()), "文档上传过于频繁，请稍后再试");
    }

    private void acquire(String userId, String keyPrefix, int permitsPerMinute, String denyMessage) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        try {
            String key = keyPrefix + userId;
            RRateLimiter limiter = redissonClient.getRateLimiter(key);
            limiter.trySetRate(RateType.OVERALL, permitsPerMinute, 1, RateIntervalUnit.MINUTES);
            if (!limiter.tryAcquire(1)) {
                log.warn("限流触发 key={} permits={}/min", key, permitsPerMinute);
                throw new BusinessException(ErrorCode.TOO_FREQUENT, denyMessage);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redisson 限流异常，本次放行：{}", e.getMessage());
        }
    }
}
