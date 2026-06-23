package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CharacterNameResolverTest {

    private final CharacterNameResolver resolver = new CharacterNameResolver();

    @Test
    void knownLocalNameOverridesApiName() {
        assertThat(resolver.resolve(22, "Luke")).isEqualTo("루크");
    }

    @Test
    void resolvesPreviouslyMissingCharacterCodes() {
        assertThat(resolver.resolve(65, null)).isEqualTo("데비&마를렌");
        assertThat(resolver.resolve(66, null)).isEqualTo("아르다");
        assertThat(resolver.resolve(67, null)).isEqualTo("아비게일");
    }

    @Test
    void resolvesLatestCharacterCodes() {
        assertThat(resolver.resolve(69, null)).isEqualTo("레니");
        assertThat(resolver.resolve(75, null)).isEqualTo("르노어");
        assertThat(resolver.resolve(81, null)).isEqualTo("니아");
        assertThat(resolver.resolve(89, null)).isEqualTo("크레이버");
    }

    @Test
    void unknownCodeUsesApiName() {
        assertThat(resolver.resolve(90, "New Character")).isEqualTo("New Character");
    }

    @Test
    void missingNameUsesFallbackLabel() {
        assertThat(resolver.resolve(90, null)).isEqualTo("실험체 90");
    }

    @Test
    void nullCodePreservesApiName() {
        assertThat(resolver.resolve(null, "Unknown")).isEqualTo("Unknown");
    }
}
