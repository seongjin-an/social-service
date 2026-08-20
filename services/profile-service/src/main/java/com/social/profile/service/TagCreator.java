package com.social.profile.service;

import com.social.common.UuidV7Generator;
import com.social.profile.domain.TagEntity;
import com.social.profile.repository.TagRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 없는 태그를 실제로 만드는 자리. <b>여기만</b> 별도 트랜잭션(REQUIRES_NEW)으로 떼어놨다.
 *
 * <p>tag_id 가 UUID v7 이라 시간순으로 커진다. 그래서 새 태그 INSERT 는 늘 PK 인덱스 맨 뒤로
 * 몰리고, 거기가 곧 supremum 갭이다. 한 트랜잭션이 태그 두 개를 연달아 INSERT 하면 첫 번째에서
 * 그 갭에 X 락을 잡고 두 번째에서 insert intention 을 기다리게 되는데, 옆 트랜잭션도 똑같이
 * 하고 있으면 서로 막혀 데드락이 난다. 실제로 동시 24로 밀었을 때 420건 났고,
 * InnoDB 리포트도 {@code index PRIMARY of table social.tag} 의 supremum 을 가리켰다.
 *
 * <p>트랜잭션을 여기서 끊으면 INSERT 한 문장이 곧 커밋이라, 갭락을 쥔 채로 다음 INSERT 를
 * 기다리는 상황 자체가 안 생긴다. 락을 하나만 잡고 끝나는 트랜잭션은 고리에 낄 수 없다.
 *
 * <p>이 경로는 <b>정말 없는 태그일 때만</b> 탄다. 어휘가 이미 깔려 있으면
 * {@link TagWriter} 의 조회에서 끝나고 여기까지 오지 않는다.
 */
@RequiredArgsConstructor
@Service
public class TagCreator {

    private final TagRepository tagRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID create(String rawName, String normalized) {
        tagRepository.upsertByNormalizedName(
            UuidV7Generator.generate().toString(), rawName, normalized);

        // 잠금 조회 — 남이 먼저 만들어 커밋한 행도 봐야 한다(일반 조회는 스냅샷에 안 잡힐 수 있다).
        return tagRepository.findByNormalizedNameForUpdate(normalized)
            .map(TagEntity::getId)
            .orElseThrow(() -> new IllegalStateException("태그 확보 실패: " + normalized));
    }
}
