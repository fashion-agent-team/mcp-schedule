package com.fashionagent.schedule.place;

import java.util.Optional;

/** 장소 이름으로 장소 종류를 찾습니다. 테스트에서 바꿔 끼울 수 있게 인터페이스로 둡니다. */
public interface PlaceLookup {

    /** 못 찾았거나 지도 API를 쓸 수 없으면 빈 값. 예외를 던지지 않습니다. */
    Optional<PlaceInfo> find(String placeName);
}
