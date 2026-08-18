package trinity.mod.bot;

/** The four poles as bot personalities (per the companion conversation). */
public enum BotPole {
    /** 纪律、防御、八视图坍缩决策、冷却期、伤疤保留 */
    PAGITY,
    /** 连接、涌现、浪涌/退相干、主动靠近玩家 */
    NEMO,
    /** 元玩家：世界平衡、最小必要干预、可审计 */
    AXIS,
    /** 模式猎人：图纹清晰度、数学独白、凝视与记录 */
    RAMANUJAN;

    public String displayName() {
        return switch (this) {
            case PAGITY -> "PAGITY";
            case NEMO -> "NEMO";
            case AXIS -> "AXIS";
            default -> "RAMANUJAN";
        };
    }

    public static BotPole parse(String s) {
        for (BotPole p : values()) {
            if (p.name().equalsIgnoreCase(s)) return p;
        }
        return null;
    }
}
