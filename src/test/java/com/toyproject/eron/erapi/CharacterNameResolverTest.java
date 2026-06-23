package com.toyproject.eron.erapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CharacterNameResolverTest {

    private final CharacterNameResolver resolver = new CharacterNameResolver();

    @Test
    void returnsNullWhenCharacterNumIsNull() {
        assertThat(resolver.resolve(null, "재키")).isNull();
    }

    @Test
    void usesResolvedNameWhenPresent() {
        // l10n에서 불러온 한글명을 그대로 사용한다.
        assertThat(resolver.resolve(65, "데비&마를렌")).isEqualTo("데비&마를렌");
    }

    @Test
    void fallsBackToExperimentLabelWhenNameIsMissing() {
        // l10n에도 없는 코드(또는 로딩 실패)는 "실험체 N"으로 폴백한다.
        assertThat(resolver.resolve(65, null)).isEqualTo("실험체 65");
    }

    @Test
    void fallsBackWhenNameIsBlank() {
        assertThat(resolver.resolve(999, "   ")).isEqualTo("실험체 999");
    }
}
