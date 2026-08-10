package com.social.message.service.channelmember;

import com.social.common.KeyPrefix;
import com.social.message.repository.channelmember.ChannelMemberRepository;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
@Service
public class ChannelMemberCacheService {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ChannelMemberRepository channelMemberRepository;

    public List<String> getRecipientIds(Long channelId) {
        String key = KeyPrefix.CHANNEL_MEMBERS + channelId;
        Set<String> cached = redisTemplate.opsForSet().members(key);

        if (cached != null && !cached.isEmpty()) {
            return List.copyOf(cached);
        }

        List<String> recipientIds = channelMemberRepository.findByChannelId(channelId)
                .stream()
                .map(m -> m.getUserId().toString())
                .toList();

        if (!recipientIds.isEmpty()) {
            redisTemplate.opsForSet().add(key, recipientIds.toArray(new String[0]));
            redisTemplate.expire(key, TTL);
        }

        return recipientIds;
    }

    public void invalidate(Long channelId) {
        redisTemplate.delete(KeyPrefix.CHANNEL_MEMBERS + channelId);
    }

    /**
     * 커밋 후에 지운다. 트랜잭션 <b>안에서</b> 지우면 이런 일이 생긴다:
     * 입장 트랜잭션이 캐시를 지움 → 커밋 전에 그 방으로 메시지가 오면 팬아웃이
     * <b>아직 커밋 안 된</b> DB 를 읽어 캐시를 재구성 → "새 멤버가 빠진" 목록이 TTL 30분 동안 굳는다.
     * 그 동안 새 멤버는 방에 있는데 메시지를 못 받는다.
     *
     * <p>트랜잭션이 없으면 즉시 지운다(무해).
     */
    public void invalidateAfterCommit(Long channelId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidate(channelId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidate(channelId);
            }
        });
    }
}
