package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fashionagent.schedule.event.Occasion;
import com.fashionagent.schedule.event.OccasionClassifier;
import com.fashionagent.schedule.event.OccasionSource;

/**
 * 일정 분위기(occasion) 정하기 단위 테스트. 앞의 네 개는 앱 레포 specs/002-schedule/spec.md US2 시나리오 그대로,
 * 나머지는 키워드·카카오 분류를 더 넓게 확인합니다.
 */
class OccasionClassifierTest {

    private final OccasionClassifier classifier = new OccasionClassifier();

    @Test
    @DisplayName("US2-1 Given 제목 \"장례식\" When 정하면 Then 장례(mourning), 근거는 제목")
    void 제목으로_장례를_정한다() {
        // when
        OccasionClassifier.Result r = classifier.classify("장례식", null);

        // then
        assertThat(r.occasion()).isEqualTo(Occasion.mourning);
        assertThat(r.source()).isEqualTo(OccasionSource.title);
    }

    @Test
    @DisplayName("US2-2 Given 제목 \"약속\", 장소 분류 '예식장' When 정하면 Then 결혼식(wedding), 근거는 장소")
    void 장소로_결혼식을_정한다() {
        // when
        OccasionClassifier.Result r = classifier.classify("약속", "가정,생활 > 결혼 > 예식장");

        // then
        assertThat(r.occasion()).isEqualTo(Occasion.wedding);
        assertThat(r.source()).isEqualTo(OccasionSource.place);
    }

    @Test
    @DisplayName("US2-3 Given 제목 \"회의\", 장소가 등산로 When 정하면 Then 제목을 우선해 업무(business), 근거는 제목")
    void 제목과_장소가_다르면_제목이_우선() {
        // when
        OccasionClassifier.Result r = classifier.classify("회의", "여행 > 관광,명소 > 산 > 등산로");

        // then
        assertThat(r.occasion()).isEqualTo(Occasion.business);
        assertThat(r.source()).isEqualTo(OccasionSource.title);
    }

    @Test
    @DisplayName("US2-4 Given 제목·장소로 정할 수 없음 When 정하면 Then 평상(casual), 근거는 기본값")
    void 못_정하면_평상() {
        // when
        OccasionClassifier.Result r = classifier.classify("약속", "음식점 > 한식");

        // then
        assertThat(r.occasion()).isEqualTo(Occasion.casual);
        assertThat(r.source()).isEqualTo(OccasionSource.fallback);
    }

    @Test
    void 제목_키워드로_정한다() {
        assertThat(classifier.fromTitle("친구 결혼식")).isEqualTo(Occasion.wedding);
        assertThat(classifier.fromTitle("할머니 조문")).isEqualTo(Occasion.mourning);
        assertThat(classifier.fromTitle("면접")).isEqualTo(Occasion.formal);
        assertThat(classifier.fromTitle("팀 회의")).isEqualTo(Occasion.business);
        assertThat(classifier.fromTitle("북한산 등산")).isEqualTo(Occasion.active);
        assertThat(classifier.fromTitle("친구랑 저녁")).isEqualTo(Occasion.casual);
    }

    @Test
    void 제목이_우선이고_못_정하면_장소_종류로() {
        assertThat(classifier.classify("친구 결혼식", "여행 > 숙박 > 호텔").occasion()).isEqualTo(Occasion.wedding);
        assertThat(classifier.classify("사촌 형 행사", "가정,생활 > 예식장").occasion()).isEqualTo(Occasion.wedding);
        assertThat(classifier.classify("주말 약속", "여행 > 관광,명소 > 산").occasion()).isEqualTo(Occasion.active);
        assertThat(classifier.classify("약속", "서비스,산업 > 장례식장").occasion()).isEqualTo(Occasion.mourning);
        assertThat(classifier.classify("약속", "음식점 > 한식").occasion()).isEqualTo(Occasion.casual);
        assertThat(classifier.classify("약속", null).occasion()).isEqualTo(Occasion.casual);
    }

    @Test
    void 무엇으로_정했는지_남긴다() {
        assertThat(classifier.classify("친구 결혼식", "여행 > 숙박 > 호텔").source()).isEqualTo(OccasionSource.title);
        assertThat(classifier.classify("약속", "가정,생활 > 예식장").source()).isEqualTo(OccasionSource.place);
        assertThat(classifier.classify("약속", null).source()).isEqualTo(OccasionSource.fallback);
    }

    @Test
    void 짧은_낱말은_마디가_같을_때만() {
        assertThat(classifier.fromPlaceCategory("서비스,산업 > 부동산")).isEmpty();
    }

    /** 2026-11-10 실제 카카오 검색 결과에서 가져온 분류들 */
    @Test
    void 실제_카카오_분류() {
        assertThat(classifier.fromPlaceCategory("가정,생활 > 결혼 > 예식장")).contains(Occasion.wedding);
        assertThat(classifier.fromPlaceCategory("가정,생활 > 장례 > 장례식장")).contains(Occasion.mourning);
        assertThat(classifier.fromPlaceCategory("가정,생활 > 장례 > 화장터")).contains(Occasion.mourning);
        assertThat(classifier.fromPlaceCategory("가정,생활 > 장례 > 납골당")).contains(Occasion.mourning);
        assertThat(classifier.fromPlaceCategory("여행 > 관광,명소 > 국립공원")).contains(Occasion.active);
        assertThat(classifier.fromPlaceCategory("여행 > 관광,명소 > 산")).contains(Occasion.active);
        assertThat(classifier.fromPlaceCategory("스포츠,레저 > 스포츠시설 > 헬스클럽 > 스포애니")).contains(Occasion.active);
        assertThat(classifier.fromPlaceCategory("교육,학문 > 학교 > 대학교")).contains(Occasion.business);
        assertThat(classifier.fromPlaceCategory("사회,공공기관 > 지방행정기관 > 구청")).contains(Occasion.business);
        assertThat(classifier.fromPlaceCategory("서비스,산업 > 전문대행 > 공간대여 > 공유오피스")).contains(Occasion.business);
        assertThat(classifier.fromPlaceCategory("여행 > 숙박 > 호텔 > 롯데호텔")).isEmpty();
        assertThat(classifier.fromPlaceCategory("음식점 > 카페 > 커피전문점 > 스타벅스")).isEmpty();
    }
}
