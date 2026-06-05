package org.shee33.act0.arcade.economy;

/**
 * 购买武器的结果，用于服务端回传客户端，在二级武器选择界面内即时提示。
 */
public enum BuyOutcome {

    /** 购买成功（已扣款并解锁）。 */
    SUCCESS,
    /** 已拥有（默认武器或先前已解锁）。 */
    ALREADY_OWNED,
    /** 余额不足。 */
    INSUFFICIENT,
    /** 经济后端不可用。 */
    UNAVAILABLE,
    /** 未知武器 / 其它错误。 */
    ERROR;

    private static final BuyOutcome[] VALUES = values();

    /** 按序号安全解析（越界返回 {@link #ERROR}）。 */
    public static BuyOutcome byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ERROR;
    }
}
