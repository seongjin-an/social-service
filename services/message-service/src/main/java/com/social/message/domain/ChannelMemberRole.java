package com.social.message.domain;

/**
 * 오픈방에서만 의미가 있다. DIRECT 채널 멤버는 NULL 이다(둘 다 대등하므로 역할이 없다).
 *
 * <p>ddl-auto=update 로 컬럼이 나중에 붙으므로 <b>기존 행은 NULL</b> — 판정은 항상
 * {@code == OWNER} 로만 하고 NULL 을 MEMBER 로 취급한다.
 */
public enum ChannelMemberRole {
    OWNER, MEMBER
}
