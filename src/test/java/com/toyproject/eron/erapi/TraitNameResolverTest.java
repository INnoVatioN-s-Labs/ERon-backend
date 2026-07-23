package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraitNameResolverTest {

    private final TraitNameResolver resolver = new TraitNameResolver();

    @Test
    void usesResolvedNameWhenPresent() {
        assertThat(resolver.resolve(999, "이미 찾은 특성")).isEqualTo("이미 찾은 특성");
    }

    @Test
    void knownCodeUsesKoreanFallbackWhenResolvedNameIsGenericFallback() {
        assertThat(resolver.resolve(7000201, "특성 7000201")).isEqualTo("취약");
        assertThat(resolver.resolve(7000401, "특성 7000401")).isEqualTo("흡혈마");
        assertThat(resolver.resolve(7000501, "특성 7000501")).isEqualTo("벽력");
        assertThat(resolver.resolve(7000601, "특성 7000601")).isEqualTo("아드레날린");
        assertThat(resolver.resolve(7000701, "특성 7000701")).isEqualTo("엑셀러레이터");
        assertThat(resolver.resolve(7100101, "특성 7100101")).isEqualTo("금강");
        assertThat(resolver.resolve(7100201, "특성 7100201")).isEqualTo("불괴");
        assertThat(resolver.resolve(7100401, "특성 7100401")).isEqualTo("빛의 수호");
        assertThat(resolver.resolve(7100501, "특성 7100501")).isEqualTo("응징");
        assertThat(resolver.resolve(7200101, "특성 7200101")).isEqualTo("초재생");
        assertThat(resolver.resolve(7200201, "특성 7200201")).isEqualTo("증폭 드론");
        assertThat(resolver.resolve(7200301, "특성 7200301")).isEqualTo("치유 드론");
        assertThat(resolver.resolve(7200501, "특성 7200501")).isEqualTo("헌신");
        assertThat(resolver.resolve(7300101, "특성 7300101")).isEqualTo("스텔라 차지");
        assertThat(resolver.resolve(7300201, "특성 7300201")).isEqualTo("도깨비불");
        assertThat(resolver.resolve(7300301, "특성 7300301")).isEqualTo("와류");
    }

    @Test
    void unknownCodeUsesGenericFallback() {
        assertThat(resolver.resolve(999, null)).isEqualTo("특성 999");
    }

    @Test
    void nullCodePreservesProvidedName() {
        assertThat(resolver.resolve(null, "Unknown")).isEqualTo("Unknown");
    }
}
