package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CharacterNameResolverTest {

    private final CharacterNameResolver resolver = new CharacterNameResolver();

    @Test
    void usesResolvedNameWhenPresent() {
        // 이름은 l10n에서 이미 한글로 들어오므로 그대로 사용한다.
        assertThat(resolver.resolve(22, "루크")).isEqualTo("루크");
    }

    @Test
    void unknownCodeUsesProvidedName() {
        assertThat(resolver.resolve(90, "New Character")).isEqualTo("New Character");
    }

    @Test
    void missingNameUsesFallbackLabel() {
        assertThat(resolver.resolve(90, null)).isEqualTo("실험체 90");
    }

    @Test
    void blankNameUsesFallbackLabel() {
        assertThat(resolver.resolve(65, "   ")).isEqualTo("실험체 65");
    }

    @Test
    void nullCodePreservesProvidedName() {
        assertThat(resolver.resolve(null, "Unknown")).isEqualTo("Unknown");
    }
}
