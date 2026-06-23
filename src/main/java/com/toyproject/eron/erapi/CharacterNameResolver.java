package com.toyproject.eron.erapi;

import java.util.Map;

/**
 * 실험체(캐릭터) 코드 → 표시용 이름을 결정하는 헬퍼.
 *
 * <p>실험체명은 {@link EternalReturnApiClient}가 공식 <b>l10n(한글)</b> 데이터에서 코드별로
 * 불러온다(신규 실험체 포함). 이 클래스는 그 이름을 받아 최종 표시명을 정하는 단일 지점이며,
 * l10n에 아직 없거나 잘못된 코드를 위한 <b>선택적 하드코딩 오버라이드</b>({@link #NAME_OVERRIDES})와
 * 마지막 폴백을 담당한다.
 *
 * <p>이름 결정 우선순위:
 * <ol>
 *   <li>{@link #NAME_OVERRIDES}에 등록된 코드면 그 이름으로 강제 컨버팅한다. (평소엔 비어 있음)</li>
 *   <li>없으면 l10n에서 불러온 이름을 그대로 쓴다.</li>
 *   <li>둘 다 없으면 {@code "실험체 {code}"}로 폴백한다.</li>
 * </ol>
 *
 * <p>예전에는 모든 실험체 한글명을 여기에 하드코딩했지만, 공식 l10n에서 자동으로 받아오도록
 * 바뀌면서 그 목록은 더 이상 필요 없다. l10n이 특정 코드를 누락/오기하는 경우에만
 * {@link #NAME_OVERRIDES}에 한 줄 추가해 바로잡는다.
 */
class CharacterNameResolver {

    /**
     * l10n을 신뢰할 수 없는 코드만 손으로 바로잡는 오버라이드. (코드 → 한글명)
     *
     * <p>평소엔 비워 둔다. l10n이 어떤 코드를 누락/오기할 때만 예: {@code Map.entry(76, "가넷")} 처럼 추가.
     */
    private static final Map<Integer, String> NAME_OVERRIDES = Map.of();

    /**
     * 실험체 코드와 l10n에서 불러온 이름으로 최종 표시명을 결정한다.
     *
     * @param characterNum 실험체 코드 (null이면 null 반환)
     * @param resolvedName l10n에서 불러온 이름 (null 가능)
     * @return 보정된 표시명. 코드가 null이면 null.
     */
    String resolve(Integer characterNum, String resolvedName) {
        if (characterNum == null) {
            return null;
        }

        String override = NAME_OVERRIDES.get(characterNum);
        if (override != null) {
            return override;
        }

        if (resolvedName != null && !resolvedName.isBlank()) {
            return resolvedName;
        }

        return "실험체 " + characterNum;
    }
}
