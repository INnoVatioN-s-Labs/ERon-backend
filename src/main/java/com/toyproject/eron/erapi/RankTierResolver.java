package com.toyproject.eron.erapi;

public final class RankTierResolver {

    public String resolve(Integer rankScore, Integer rank) {
        if (rankScore == null || rankScore < 0) {
            return null;
        }

        if (rankScore >= 7400) {
            if (rank != null && rank > 0 && rank <= 300) {
                return "이터니티";
            }
            if (rank != null && rank > 0 && rank <= 1000) {
                return "데미갓";
            }
            return "미스릴";
        }

        if (rankScore >= 7150) {
            return "메테오라이트 1";
        }
        if (rankScore >= 6900) {
            return "메테오라이트 2";
        }
        if (rankScore >= 6650) {
            return "메테오라이트 3";
        }
        if (rankScore >= 6400) {
            return "메테오라이트 4";
        }
        if (rankScore >= 6050) {
            return "다이아몬드 1";
        }
        if (rankScore >= 5700) {
            return "다이아몬드 2";
        }
        if (rankScore >= 5350) {
            return "다이아몬드 3";
        }
        if (rankScore >= 5000) {
            return "다이아몬드 4";
        }
        if (rankScore >= 4650) {
            return "플래티넘 1";
        }
        if (rankScore >= 4300) {
            return "플래티넘 2";
        }
        if (rankScore >= 3950) {
            return "플래티넘 3";
        }
        if (rankScore >= 3600) {
            return "플래티넘 4";
        }
        if (rankScore >= 3300) {
            return "골드 1";
        }
        if (rankScore >= 3000) {
            return "골드 2";
        }
        if (rankScore >= 2700) {
            return "골드 3";
        }
        if (rankScore >= 2400) {
            return "골드 4";
        }
        if (rankScore >= 2150) {
            return "실버 1";
        }
        if (rankScore >= 1900) {
            return "실버 2";
        }
        if (rankScore >= 1650) {
            return "실버 3";
        }
        if (rankScore >= 1400) {
            return "실버 4";
        }
        if (rankScore >= 1200) {
            return "브론즈 1";
        }
        if (rankScore >= 1000) {
            return "브론즈 2";
        }
        if (rankScore >= 800) {
            return "브론즈 3";
        }
        if (rankScore >= 600) {
            return "브론즈 4";
        }
        if (rankScore >= 450) {
            return "아이언 1";
        }
        if (rankScore >= 300) {
            return "아이언 2";
        }
        if (rankScore >= 150) {
            return "아이언 3";
        }
        return "아이언 4";
    }
}
