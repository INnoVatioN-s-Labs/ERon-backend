package com.toyproject.eron.erapi;

/**
 * 실험체 코드 → 표시명을 결정하는 헬퍼.
 *
 * <p>실험체 이름은 {@link EternalReturnApiClient}가 공식 <b>l10n(한글)</b> 데이터에서 코드별로
 * 불러온다(트레잇/전술스킬용으로 이미 받고 있는 그 l10n을 재사용). 이 클래스는 그 이름을 받아
 * 최종 표시명을 정하고, 이름이 없을 때 {@code "실험체 {code}"} 폴백만 담당한다.
 *
 * <p>이전에는 실험체 1~89 한글명을 전부 이 클래스에 하드코딩했지만, l10n 자동 로딩으로 대체되어
 * 그 목록을 제거했다. 덕분에 신규 실험체가 출시돼도 수동으로 이름을 추가할 필요가 없다.
 *
 * <p>이름 결정:
 * <ol>
 *   <li>l10n에서 불러온 이름이 있으면 그대로 쓴다.</li>
 *   <li>없고 코드가 있으면 {@code "실험체 {code}"}로 폴백한다.</li>
 *   <li>코드도 없으면 받은 이름(보통 null)을 그대로 반환한다.</li>
 * </ol>
 */
public final class CharacterNameResolver {

    public String resolve(Integer characterCode, String resolvedName) {
        if (resolvedName != null && !resolvedName.isBlank()) {
            return resolvedName;
        }

        if (characterCode == null) {
            return resolvedName;
        }

        return "실험체 " + characterCode;
    }
}
