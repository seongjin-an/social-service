package com.social.message.service.channelmember;

import com.social.common.KeyPrefix;
import com.social.message.repository.channelmember.ChannelMemberRepository;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
}
