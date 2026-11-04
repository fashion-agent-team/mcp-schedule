package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fashionagent.schedule.event.Occasion;
import com.fashionagent.schedule.event.OccasionClassifier;

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
        assertThat(classifier.classify("친구 결혼식", "여행 > 숙박 > 호텔")).isEqualTo(Occasion.wedding);
        assertThat(classifier.classify("사촌 형 행사", "가정,생활 > 예식장")).isEqualTo(Occasion.wedding);
        assertThat(classifier.classify("주말 약속", "여행 > 관광,명소 > 산")).isEqualTo(Occasion.active);
        assertThat(classifier.classify("약속", "서비스,산업 > 장례식장")).isEqualTo(Occasion.mourning);
        assertThat(classifier.classify("약속", "음식점 > 한식")).isEqualTo(Occasion.casual);
        assertThat(classifier.classify("약속", null)).isEqualTo(Occasion.casual);
    }

    @Test
    void 짧은_낱말은_마디가_같을_때만() {
        assertThat(classifier.fromPlaceCategory("서비스,산업 > 부동산")).isEmpty();
    }
}
