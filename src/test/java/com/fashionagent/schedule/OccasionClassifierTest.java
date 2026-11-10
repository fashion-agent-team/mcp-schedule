package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fashionagent.schedule.event.Occasion;
import com.fashionagent.schedule.event.OccasionClassifier;
import com.fashionagent.schedule.event.OccasionSource;

class OccasionClassifierTest {

    private final OccasionClassifier classifier = new OccasionClassifier();

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
