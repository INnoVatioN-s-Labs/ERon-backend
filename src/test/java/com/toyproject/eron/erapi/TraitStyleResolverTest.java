package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TraitStyleResolverTest {

    private final TraitStyleResolver resolver = new TraitStyleResolver();

    @Test
    void resolvesTraitStyleByCodeGroup() {
        assertThat(resolver.resolve(7000401)).isEqualTo("파괴");
        assertThat(resolver.resolve(7110701)).isEqualTo("저항");
        assertThat(resolver.resolve(7200101)).isEqualTo("지원");
        assertThat(resolver.resolve(7300101)).isEqualTo("혼돈");
        assertThat(resolver.resolve(7900101)).isEqualTo("혼돈");
    }

    @Test
    void resolvesTraitStyleFromFirstSecondSubTrait() {
        assertThat(resolver.resolveFromSecondSubTraits(List.of(7110701, 7110601))).isEqualTo("저항");
    }

    @Test
    void unknownCodeReturnsNull() {
        assertThat(resolver.resolve(1)).isNull();
        assertThat(resolver.resolveFromSecondSubTraits(List.of())).isNull();
        assertThat(resolver.resolveFromSecondSubTraits(null)).isNull();
    }
}
