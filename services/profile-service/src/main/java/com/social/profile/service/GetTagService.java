package com.social.profile.service;

import com.social.profile.domain.TagEntity;
import com.social.profile.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심사 태그 피커 조회 (고정 어휘). q 로 부분일치 검색, 없으면 인기 태그 상위.
 * 자유입력을 유도하지 않도록 서버가 통제된 어휘 목록을 내려준다.
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class GetTagService {

    private final TagRepository tagRepository;

    public List<TagResult> searchTags(String q) {
        List<TagEntity> tags = (q == null || q.isBlank())
            ? tagRepository.findTop20ByOrderByUsageCountDesc()
            : tagRepository.findTop20ByNormalizedNameContainingOrderByUsageCountDesc(TagEntity.normalize(q));
        return tags.stream().map(TagResult::from).toList();
    }
}
