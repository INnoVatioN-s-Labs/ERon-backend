package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RankTierResolverTest {

    private final RankTierResolver resolver = new RankTierResolver();

    @Test
    void resolvesTierByRankScore() {
        assertThat(resolver.resolve(0, null)).isEqualTo("아이언 4");
        assertThat(resolver.resolve(450, null)).isEqualTo("아이언 1");
        assertThat(resolver.resolve(600, null)).isEqualTo("브론즈 4");
        assertThat(resolver.resolve(1200, null)).isEqualTo("브론즈 1");
        assertThat(resolver.resolve(1400, null)).isEqualTo("실버 4");
        assertThat(resolver.resolve(2150, null)).isEqualTo("실버 1");
        assertThat(resolver.resolve(2400, null)).isEqualTo("골드 4");
        assertThat(resolver.resolve(3300, null)).isEqualTo("골드 1");
        assertThat(resolver.resolve(3600, null)).isEqualTo("플래티넘 4");
        assertThat(resolver.resolve(4650, null)).isEqualTo("플래티넘 1");
        assertThat(resolver.resolve(5000, null)).isEqualTo("다이아몬드 4");
        assertThat(resolver.resolve(6050, null)).isEqualTo("다이아몬드 1");
        assertThat(resolver.resolve(6400, null)).isEqualTo("메테오라이트 4");
        assertThat(resolver.resolve(7150, null)).isEqualTo("메테오라이트 1");
        assertThat(resolver.resolve(7400, null)).isEqualTo("미스릴");
    }

    @Test
    void resolvesTopRankTiersWhenRankIsAvailable() {
        assertThat(resolver.resolve(7400, 300)).isEqualTo("이터니티");
        assertThat(resolver.resolve(7400, 301)).isEqualTo("데미갓");
        assertThat(resolver.resolve(7400, 1000)).isEqualTo("데미갓");
        assertThat(resolver.resolve(7400, 1001)).isEqualTo("미스릴");
    }

    @Test
    void invalidRankScoreResolvesToNull() {
        assertThat(resolver.resolve(null, 1)).isNull();
        assertThat(resolver.resolve(-1, 1)).isNull();
    }
}
