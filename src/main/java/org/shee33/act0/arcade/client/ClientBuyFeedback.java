package org.shee33.act0.arcade.client;

import org.shee33.act0.arcade.economy.BuyOutcome;

/**
 * 客户端购买反馈缓存：保存最近一次购买结果，供二级武器选择界面在界面内即时提示，无需切到聊天栏查看。
 */
public final class ClientBuyFeedback {

    private static String key = "";
    private static BuyOutcome outcome = BuyOutcome.ERROR;
    private static double balance;
    private static long timestampMs;

    private ClientBuyFeedback() {
    }

    /** 接收服务端购买结果。 */
    public static void accept(String key, BuyOutcome outcome, double balance) {
        ClientBuyFeedback.key = key == null ? "" : key;
        ClientBuyFeedback.outcome = outcome;
        ClientBuyFeedback.balance = balance;
        ClientBuyFeedback.timestampMs = System.currentTimeMillis();
    }

    public static String key() {
        return key;
    }

    public static BuyOutcome outcome() {
        return outcome;
    }

    public static double balance() {
        return balance;
    }

    public static long timestampMs() {
        return timestampMs;
    }
}
