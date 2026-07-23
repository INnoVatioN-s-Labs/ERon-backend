package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraitIconCodeResolverTest {

    private final TraitIconCodeResolver resolver = new TraitIconCodeResolver();

    @Test
    void knownTraitCodesResolveToIconCodes() {
        assertThat(resolver.resolve(7000201)).isEqualTo(7000201);
        assertThat(resolver.resolve(7000401)).isEqualTo(7000401);
        assertThat(resolver.resolve(7000501)).isEqualTo(7000501);
        assertThat(resolver.resolve(7000601)).isEqualTo(7000601);
        assertThat(resolver.resolve(7000701)).isEqualTo(7000701);
        assertThat(resolver.resolve(7100101)).isEqualTo(7100101);
        assertThat(resolver.resolve(7100201)).isEqualTo(7100201);
        assertThat(resolver.resolve(7100401)).isEqualTo(7100401);
        assertThat(resolver.resolve(7100501)).isEqualTo(7100501);
        assertThat(resolver.resolve(7200101)).isEqualTo(7200101);
        assertThat(resolver.resolve(7200201)).isEqualTo(7200201);
        assertThat(resolver.resolve(7200301)).isEqualTo(7200301);
        assertThat(resolver.resolve(7200501)).isEqualTo(7200501);
        assertThat(resolver.resolve(7300101)).isEqualTo(7300101);
        assertThat(resolver.resolve(7300201)).isEqualTo(7300201);
        assertThat(resolver.resolve(7300301)).isEqualTo(7300301);
    }

    @Test
    void unknownTraitCodeUsesOriginalCodeAsFallback() {
        assertThat(resolver.resolve(9999999)).isEqualTo(9999999);
    }

    @Test
    void nullTraitCodeResolvesToNull() {
        assertThat(resolver.resolve(null)).isNull();
    }
}
