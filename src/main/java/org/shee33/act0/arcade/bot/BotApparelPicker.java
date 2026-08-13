package org.shee33.act0.arcade.bot;

import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelRegistry;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.ApparelSlot;

import java.util.List;
import java.util.Random;

/**
 * 给 AI 士兵随机搭配护甲：每个槽位从目录里独立抽一件。MC-free，可单测。
 *
 * <p><b>逐槽独立抽取而非整套抽取</b>，是为了得到"杂牌军"的观感——真实战场上士兵的装具本就不成套。
 * 若按套抽，同一批 bot 会呈现出几种一眼可辨的固定造型，反而更假。
 *
 * <p><b>目录为空是正常情况，不是错误。</b>护甲目录（{@code config/act0_arcade/apparel/}）默认不写
 * 种子模板，全靠管理员用 {@code /arcade armory addapparel} 录入。因此本类对空池、对缺某个槽位一律
 * 静默跳过：管理员只录了头盔，bot 就只戴头盔，而不是抛异常或整体不穿。
 *
 * <p>随机源由调用方注入，便于用固定种子单测，也便于让同一个 bot 的造型在整局内稳定
 * （见 {@code BotApparelApplier}）。
 */
public final class BotApparelPicker {

    private BotApparelPicker() {
    }

    /**
     * 逐槽随机搭配一套护甲。
     *
     * @param registry 护甲目录；{@code null} 或空目录返回空选择
     * @param rng      随机源
     * @return 每个有货槽位各含一件的选择；无货槽位不设值（应用时会被留空）
     */
    public static ApparelSelection pickRandom(ApparelRegistry registry, Random rng) {
        ApparelSelection selection = new ApparelSelection();
        if (registry == null || rng == null) {
            return selection;
        }
        for (ApparelSlot slot : ApparelSlot.values()) {
            List<ApparelItem> pool = registry.itemsForSlot(slot);
            if (pool.isEmpty()) {
                continue;
            }
            selection.set(slot, pool.get(rng.nextInt(pool.size())).key());
        }
        return selection;
    }

    /**
     * 目录里是否有任何可穿的护甲。
     *
     * <p>供调用方在完全无货时跳过整条应用流程，避免每次复活都白跑一遍逐槽循环与一次存档写入。
     */
    public static boolean hasAnyApparel(ApparelRegistry registry) {
        if (registry == null) {
            return false;
        }
        for (ApparelSlot slot : ApparelSlot.values()) {
            if (!registry.itemsForSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
