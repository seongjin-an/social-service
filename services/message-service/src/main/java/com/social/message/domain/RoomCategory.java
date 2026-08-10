package com.social.message.domain;

/**
 * 오픈채팅 게시판의 탭. 자유 문자열이 아니라 enum 인 이유는 게시판이 탐색 화면이기 때문이다 —
 * 오타 카테고리("운동 ", "운동/러닝")가 섞이면 탭 하나가 사실상 죽는다.
 *
 * <p>DB 에는 VARCHAR(50) 에 이름 그대로 저장된다({@code EnumType.STRING}).
 * 값을 지울 때는 기존 행이 남아 역직렬화가 깨지므로 <b>추가만</b> 한다.
 */
public enum RoomCategory {
    HOBBY,    // 취미
    SPORTS,   // 운동
    FOOD,     // 맛집
    MUSIC,    // 음악
    TRAVEL,   // 여행
    ETC       // 기타
}
