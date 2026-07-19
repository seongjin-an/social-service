package com.social.profile.service;

import com.social.common.UuidV7Generator;
import com.social.profile.domain.TagEntity;
import com.social.profile.repository.TagRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TagWriter {

    private final TagRepository tagRepository;

    /**
     * 태그를 정규화 이름 기준으로 get-or-create 하고 tag_id 를 반환한다.
     *
     * REQUIRES_NEW 로 독립 트랜잭션에서 처리하는 이유:
     *   동시에 같은 새 태그를 만들면 uk(normalized_name) 위반이 나는데, 그 실패를
     *   이 트랜잭션 안에 가둬 롤백시키고 호출측(프로필 저장 메인 트랜잭션)은 오염되지 않게 한다.
     *   승자 트랜잭션이 commit 한 행은 호출측에서 재조회로 흡수한다(ProfileWriteService.resolveTagId).
     *   태그는 어휘(vocabulary)라 프로필 저장이 실패해도 남는 게 자연스럽다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID getOrCreateId(String rawName) {
        String normalized = TagEntity.normalize(rawName);
        return tagRepository.findByNormalizedName(normalized)
            .map(TagEntity::getId)
            .orElseGet(() -> tagRepository.save(TagEntity.of(UuidV7Generator.generate(), rawName)).getId());
    }
}
