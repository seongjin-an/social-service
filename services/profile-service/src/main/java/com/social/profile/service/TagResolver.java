package com.social.profile.service;

import com.social.profile.domain.TagEntity;
import com.social.profile.repository.TagRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 태그를 정규화 이름 기준으로 get-or-create 하여 tagId 를 해석한다.
 * TagWriter(REQUIRES_NEW)에서 동시 생성 레이스로 UNIQUE 위반이 나면, 그 실패는 별도 트랜잭션에
 * 갇혀 롤백되므로 호출측(현재 트랜잭션)에서 재조회로 흡수한다. (저장/수정 양쪽에서 공용)
 */
@RequiredArgsConstructor
@Component
public class TagResolver {

    private final TagWriter tagWriter;
    private final TagRepository tagRepository;

    public UUID resolveId(String rawName) {
        try {
            return tagWriter.getOrCreateId(rawName);
        } catch (DataIntegrityViolationException race) {
            return tagRepository.findByNormalizedName(TagEntity.normalize(rawName))
                .map(TagEntity::getId)
                .orElseThrow(() -> race);
        }
    }
}
