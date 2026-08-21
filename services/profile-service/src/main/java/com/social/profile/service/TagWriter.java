package com.social.profile.service;

import com.social.profile.domain.TagEntity;
import com.social.profile.repository.TagRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TagWriter {

    private final TagRepository tagRepository;
    private final TagCreator tagCreator;

    /**
     * 태그를 정규화 이름 기준으로 get-or-create 하고 tag_id 를 반환한다.
     *
     * <p>있으면 조회로 끝난다 — 락도 안 잡고 트랜잭션도 안 나눈다. 어휘가 고정이라 대부분 여기서
     * 끝나고, 그래서 프로필 저장은 커넥션 하나로 돌아간다.
     *
     * <p>없을 때만 {@link TagCreator} 로 넘긴다. 거기가 별도 트랜잭션인 이유는 그쪽 주석에 적어뒀다.
     * 조회가 스냅샷 때문에 남이 방금 만든 행을 놓쳐도 문제가 안 되는데, 그때도 생성 경로가
     * upsert 로 같은 행을 집어오기 때문이다.
     */
    @Transactional
    public UUID getOrCreateId(String rawName) {
        String normalized = TagEntity.normalize(rawName);

        return tagRepository.findByNormalizedName(normalized)
            .map(TagEntity::getId)
            .orElseGet(() -> tagCreator.create(rawName, normalized));
    }
}
