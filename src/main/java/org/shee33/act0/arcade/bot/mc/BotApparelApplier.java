package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.level.ServerPlayer;
import org.shee33.act0.arcade.bot.BotApparelPicker;
import org.shee33.act0.arcade.loadout.ApparelRegistry;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.mc.LoadoutApplier;

import java.util.Random;
import java.util.Set;

/**
 * 给 AI 士兵穿上随机搭配的护甲，复用真人玩家那条 {@link LoadoutApplier#applyApparel} 通路。
 *
 * <p><b>随机源按 bot 身份播种，而不是每次调用重摇。</b>这一条决定了整个实现的形状：
 * <ul>
 *   <li>同一个 bot 的造型<b>恒定</b>——每个士兵有自己的一套装具，而不是每次复活换一身，
 *       后者从玩家视角看是明显的穿模级怪异。</li>
 *   <li>因为结果只由 UUID 决定，本方法是<b>幂等</b>的。{@code ArcadeMatch} 在每回合开始与每次复活
 *       都会重跑一遍配装，幂等意味着这些重跑不会把护甲换掉，也<b>不需要把选择写进
 *       {@code ArcadeApparelStore}</b>——若走存档，反而要额外处理"存档里是空选择就会把刚穿的扒光"。</li>
 *   <li>bot 的 UUID 由名字确定性派生（见 {@code BotNames#uuidOf}），故造型跨重启可复现，
 *       与 {@code BotWeaponController} 的随机种子取法一致。</li>
 * </ul>
 *
 * <p><b>解锁门禁对 bot 一律放行。</b>{@code unlocksProvider} 查 bot 的 UUID 只会得到空集，于是所有
 * 未标 {@code isDefault} 的护甲都会被 {@link LoadoutApplier#applyApparel} 清成空槽——bot 会光着。
 * 这里改传目录的全部 key 作为"已解锁集"，既让 bot 能穿全目录，又完全不触碰真人的解锁记录
 * （不写 {@code ArcadePlayerUnlocks}，也不给 bot UUID 留下持久化痕迹）。
 */
public final class BotApparelApplier {

    private BotApparelApplier() {
    }

    /**
     * 按 bot 身份随机搭配并穿上护甲；护甲目录为空时什么也不做。
     *
     * @param bot      目标 bot（调用方须已确认是 bot）
     * @param registry 护甲目录
     * @param applier  与真人共用的配装应用器
     */
    public static void applyRandom(ServerPlayer bot, ApparelRegistry registry, LoadoutApplier applier) {
        if (bot == null || registry == null || applier == null) {
            return;
        }
        if (!BotApparelPicker.hasAnyApparel(registry)) {
            return;
        }
        ApparelSelection selection =
                BotApparelPicker.pickRandom(registry, new Random(bot.getUUID().hashCode()));
        Set<String> allKeys = registry.all().keySet();
        applier.applyApparel(bot, selection, registry, allKeys);
    }
}
