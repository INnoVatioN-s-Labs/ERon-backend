package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TacticalSkillNameResolverTest {

    private final TacticalSkillNameResolver resolver = new TacticalSkillNameResolver();

    @Test
    void usesResolvedNameWhenPresent() {
        assertThat(resolver.resolve(999, "사용자 지정 전술스킬")).isEqualTo("사용자 지정 전술스킬");
    }

    @Test
    void knownCodeUsesKoreanFallbackWhenResolvedNameIsGenericFallback() {
        assertThat(resolver.resolve(30, "전술 스킬 30")).isEqualTo("블링크");
        assertThat(resolver.resolve(40, "전술 스킬 40")).isEqualTo("퀘이크");
        assertThat(resolver.resolve(50, "전술 스킬 50")).isEqualTo("프로토콜 위반");
        assertThat(resolver.resolve(60, "전술 스킬 60")).isEqualTo("붉은 폭풍");
        assertThat(resolver.resolve(70, "전술 스킬 70")).isEqualTo("초월");
        assertThat(resolver.resolve(80, "전술 스킬 80")).isEqualTo("아티팩트");
        assertThat(resolver.resolve(90, "전술 스킬 90")).isEqualTo("무효화");
        assertThat(resolver.resolve(110, "전술 스킬 110")).isEqualTo("강한 결속");
        assertThat(resolver.resolve(120, "전술 스킬 120")).isEqualTo("스트라이더 - A13");
        assertThat(resolver.resolve(130, "전술 스킬 130")).isEqualTo("진실의 칼날");
        assertThat(resolver.resolve(140, "전술 스킬 140")).isEqualTo("블링크");
        assertThat(resolver.resolve(150, "전술 스킬 150")).isEqualTo("치유의 바람");
        assertThat(resolver.resolve(160, "전술 스킬 160")).isEqualTo("리펄서 미사일");
        assertThat(resolver.resolve(170, "전술 스킬 170")).isEqualTo("플라즈마 대시");
        assertThat(resolver.resolve(190, "전술 스킬 190")).isEqualTo("라이트 윙");
        assertThat(resolver.resolve(50030, "전술 스킬 50030")).isEqualTo("롤링썬더");
        assertThat(resolver.resolve(500030, "전술 스킬 500030")).isEqualTo("롤링썬더");
        assertThat(resolver.resolve(500120, "전술 스킬 500120")).isEqualTo("블링크");
        assertThat(resolver.resolve(500130, "전술 스킬 500130")).isEqualTo("퀘이크");
        assertThat(resolver.resolve(500160, "전술 스킬 500160")).isEqualTo("초월");
        assertThat(resolver.resolve(500180, "전술 스킬 500180")).isEqualTo("무효화");
        assertThat(resolver.resolve(500250, "전술 스킬 500250")).isEqualTo("리펄서 미사일");
        assertThat(resolver.resolve(500260, "전술 스킬 500260")).isEqualTo("플라즈마 대시");
        assertThat(resolver.resolve(500270, "전술 스킬 500270")).isEqualTo("쇠약");
    }

    @Test
    void unknownCodeUsesGenericFallback() {
        assertThat(resolver.resolve(999, null)).isEqualTo("전술 스킬 999");
    }

    @Test
    void nullCodePreservesProvidedName() {
        assertThat(resolver.resolve(null, "Unknown")).isEqualTo("Unknown");
    }
}
