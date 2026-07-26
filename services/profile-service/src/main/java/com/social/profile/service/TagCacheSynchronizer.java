package com.social.profile.service;

import com.social.profile.repository.TagRedisRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 프로필 태그 집합의 Redis 캐시(tags:{profileId})를 <b>DB 커밋 성공 후에만</b> 갱신한다.
 * 롤백 시 유령 집합이 안 남고, Redis 반영만 실패하면 다음 저장/수정에서 복구된다(멱등). 저장·수정 공용.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class TagCacheSynchronizer {

    private final TagRedisRepository tagRedisRepository;

    public void syncAfterCommit(UUID profileId, List<UUID> tagIds) {
        List<String> tokens = tagIds.stream().map(UUID::toString).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    tagRedisRepository.replaceTags(profileId, tokens);
                } catch (Exception e) {
                    log.warn("tags Redis SET 반영 실패(다음 갱신에서 복구): profileId={}", profileId, e);
                }
            }
        });
    }
}
