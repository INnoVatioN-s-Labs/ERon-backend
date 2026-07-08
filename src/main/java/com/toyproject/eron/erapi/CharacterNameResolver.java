package com.toyproject.eron.erapi;

import java.util.Map;

/**
 * 실험체 코드 → 표시명을 결정하는 헬퍼.
 *
 * <p>실험체 이름은 {@link EternalReturnApiClient}가 공식 <b>l10n(한글)</b> 데이터에서 코드별로
 * 불러온다(트레잇/전술스킬용으로 이미 받고 있는 그 l10n을 재사용). 이 클래스는 그 이름을 받아 최종 표시명을 정한다.
 *
 * <p>공식 l10n이 일시적으로 실패하거나 영어 메타데이터로 폴백될 수 있으므로, 이미 알고 있는 실험체는
 * 로컬 한글명을 안전망으로 사용한다.
 *
 * <p>이름 결정:
 * <ol>
 *   <li>l10n에서 불러온 한글 이름이 있으면 그대로 쓴다.</li>
 *   <li>없거나 영어 이름이면 로컬 한글명을 쓴다.</li>
 *   <li>로컬 한글명도 없고 코드가 있으면 제공된 이름 또는 {@code "실험체 {code}"}로 폴백한다.</li>
 *   <li>코드도 없으면 받은 이름(보통 null)을 그대로 반환한다.</li>
 * </ol>
 */
public final class CharacterNameResolver {

    private static final Map<Integer, String> KNOWN_KOREAN_NAMES_BY_CODE = Map.ofEntries(
            Map.entry(1, "재키"), Map.entry(2, "아야"), Map.entry(3, "피오라"),
            Map.entry(4, "매그너스"), Map.entry(5, "자히르"), Map.entry(6, "나딘"),
            Map.entry(7, "현우"), Map.entry(8, "하트"), Map.entry(9, "아이솔"),
            Map.entry(10, "리 다이린"), Map.entry(11, "유키"), Map.entry(12, "혜진"),
            Map.entry(13, "쇼우"), Map.entry(14, "키아라"), Map.entry(15, "시셀라"),
            Map.entry(16, "실비아"), Map.entry(17, "아드리아나"), Map.entry(18, "쇼이치"),
            Map.entry(19, "엠마"), Map.entry(20, "레녹스"), Map.entry(21, "로지"),
            Map.entry(22, "루크"), Map.entry(23, "캐시"), Map.entry(24, "아델라"),
            Map.entry(25, "버니스"), Map.entry(26, "바바라"), Map.entry(27, "알렉스"),
            Map.entry(28, "수아"), Map.entry(29, "레온"), Map.entry(30, "일레븐"),
            Map.entry(31, "리오"), Map.entry(32, "윌리엄"), Map.entry(33, "니키"),
            Map.entry(34, "나타폰"), Map.entry(35, "얀"), Map.entry(36, "에바"),
            Map.entry(37, "다니엘"), Map.entry(38, "제니"), Map.entry(39, "카밀로"),
            Map.entry(40, "클로에"), Map.entry(41, "요한"), Map.entry(42, "비앙카"),
            Map.entry(43, "셀린"), Map.entry(44, "에키온"), Map.entry(45, "마이"),
            Map.entry(46, "에이든"), Map.entry(47, "라우라"), Map.entry(48, "띠아"),
            Map.entry(49, "펠릭스"), Map.entry(50, "엘레나"), Map.entry(51, "프리야"),
            Map.entry(52, "아디나"), Map.entry(53, "마커스"), Map.entry(54, "칼라"),
            Map.entry(55, "에스텔"), Map.entry(56, "피올로"), Map.entry(57, "마르티나"),
            Map.entry(58, "헤이즈"), Map.entry(59, "아이작"), Map.entry(60, "타지아"),
            Map.entry(61, "이렘"), Map.entry(62, "테오도르"), Map.entry(63, "리안"),
            Map.entry(64, "바냐"), Map.entry(65, "데비&마를렌"), Map.entry(66, "아르다"),
            Map.entry(67, "아비게일"), Map.entry(68, "알론소"), Map.entry(69, "레니"),
            Map.entry(70, "츠바메"), Map.entry(71, "케네스"), Map.entry(72, "카티야"),
            Map.entry(73, "샬럿"), Map.entry(74, "다르코"), Map.entry(75, "르노어"),
            Map.entry(76, "가넷"), Map.entry(77, "유민"), Map.entry(78, "히스이"),
            Map.entry(79, "유스티나"), Map.entry(80, "이슈트반"), Map.entry(81, "니아"),
            Map.entry(82, "슈린"), Map.entry(83, "헨리"), Map.entry(84, "블레어"),
            Map.entry(85, "미르카"), Map.entry(86, "펜리르"), Map.entry(87, "코렐라인"),
            Map.entry(88, "비형"), Map.entry(89, "크레이버")
    );

    public String resolve(Integer characterCode, String resolvedName) {
        if (characterCode == null) {
            return resolvedName;
        }

        if (containsHangul(resolvedName)) {
            return resolvedName;
        }

        String knownKoreanName = KNOWN_KOREAN_NAMES_BY_CODE.get(characterCode);
        if (knownKoreanName != null) {
            return knownKoreanName;
        }

        return resolvedName == null || resolvedName.isBlank() ? "실험체 " + characterCode : resolvedName;
    }

    private boolean containsHangul(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return value.chars().anyMatch(character -> character >= 0xAC00 && character <= 0xD7A3);
    }
}
