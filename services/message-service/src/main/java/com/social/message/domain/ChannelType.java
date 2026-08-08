package com.social.message.domain;

/**
 * DIRECT — 매칭으로 자동 생성되는 1:1 채널(match_id 에 귀속).
 * OPEN   — REST 로 만드는 일반/공개 채널.
 */
public enum ChannelType {
    DIRECT, OPEN
}
