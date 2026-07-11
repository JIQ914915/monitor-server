package com.lzzh.monitor.common.enums;

/** 健康等级（§10.1：优秀≥90 / 良好≥75 / 警告≥60 / 严重&lt;60）。 */
public enum HealthLevel {

    EXCELLENT("优秀", 90),
    GOOD("良好", 75),
    WARNING("警告", 60),
    CRITICAL("严重", 0);

    private final String label;
    private final int min;

    HealthLevel(String label, int min) {
        this.label = label;
        this.min = min;
    }

    public String label() {
        return label;
    }

    /** 按健康分映射等级。 */
    public static HealthLevel of(int score) {
        if (score >= EXCELLENT.min) return EXCELLENT;
        if (score >= GOOD.min) return GOOD;
        if (score >= WARNING.min) return WARNING;
        return CRITICAL;
    }
}
