package org.shee33.act0.arcade.match;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.shee33.act0.arcade.loadout.mc.LoadoutApplier;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 对局管理器：持有所有进行中的 {@link ArcadeMatch}，把 Forge 事件（死亡、服务器刻）路由给对应对局，
 * 并回收已结束对局。
 *
 * <p>注册到 Forge 事件总线（{@code MinecraftForge.EVENT_BUS.register(manager)}）。
 */
public final class MatchManager {

    private final Map<String, ArcadeMatch> matches = new ConcurrentHashMap<>();
    /** 玩家 → 所在对局 id，便于 O(1) 路由死亡事件。掉线玩家仍保留以支持重连。 */
    private final Map<UUID, String> matchByPlayer = new ConcurrentHashMap<>();
    /** 对局结束回调（用于回收对应房间）；可为 {@code null}。 */
    private Consumer<String> endListener;

    /** 登记一个已创建好的对局，开始接收事件驱动。 */
    public void register(ArcadeMatch match, Collection<UUID> participants) {
        matches.put(match.matchId(), match);
        for (UUID id : participants) {
            matchByPlayer.put(id, match.matchId());
        }
    }

    /** 设置对局结束回调（由 {@link ArcadeServices} 装配，指向 {@link RoomManager#onMatchEnded}）。 */
    public void setEndListener(Consumer<String> endListener) {
        this.endListener = endListener;
    }

    /** 把一名中途加入的玩家登记到指定对局。 */
    public void addParticipant(String matchId, UUID playerId) {
        if (matches.containsKey(matchId)) {
            matchByPlayer.put(playerId, matchId);
        }
    }

    public ArcadeMatch get(String matchId) {
        return matches.get(matchId);
    }

    public Collection<ArcadeMatch> all() {
        return java.util.List.copyOf(matches.values());
    }

    public boolean isArenaInUse(String arenaId) {
        if (arenaId == null) {
            return false;
        }
        for (ArcadeMatch match : matches.values()) {
            if (!match.isEnded() && arenaId.equals(match.arenaId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isInMatch(UUID playerId) {
        return matchByPlayer.containsKey(playerId);
    }

    public boolean leaveMatch(ServerPlayer player) {
        String matchId = matchByPlayer.get(player.getUUID());
        if (matchId == null) {
            return false;
        }
        ArcadeMatch match = matches.get(matchId);
        if (match == null) {
            matchByPlayer.remove(player.getUUID());
            return false;
        }
        boolean ok = match.quitPlayer(player);
        if (ok) {
            matchByPlayer.remove(player.getUUID());
        }
        return ok;
    }

    public boolean canChangeLoadout(UUID playerId) {
        String matchId = matchByPlayer.get(playerId);
        if (matchId == null) {
            return true;
        }
        ArcadeMatch match = matches.get(matchId);
        return match != null && match.canChangeLoadout(playerId);
    }

    /** 玩家所在对局 id；不在任何对局返回 {@code null}。 */
    public String matchIdOf(UUID playerId) {
        return matchByPlayer.get(playerId);
    }

    /**
     * 玩家登出：<b>保留</b>其与对局的绑定（以便重连归位），仅通知对局将其标记为掉线。
     */
    public void onPlayerLogout(UUID playerId) {
        String matchId = matchByPlayer.get(playerId);
        if (matchId == null) {
            return;
        }
        ArcadeMatch match = matches.get(matchId);
        if (match != null) {
            match.onPlayerLeft(playerId);
        }
    }

    /** 玩家登入：若其属于某进行中的对局，则归位重连。 */
    public void onPlayerLogin(ServerPlayer player) {
        String matchId = matchByPlayer.get(player.getUUID());
        if (matchId == null) {
            return;
        }
        ArcadeMatch match = matches.get(matchId);
        if (match != null) {
            match.onPlayerRejoin(player);
        } else {
            matchByPlayer.remove(player.getUUID());
        }
    }

    public int activeCount() {
        return matches.size();
    }

    /** 清理所有已加载世界中的街机弹药补给箱。 */
    public int cleanupAmmoCrates(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item && isAmmoCrate(item.getItem())) {
                    item.discard();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** 中止并移除全部对局（如服务器停止）。 */
    public void abortAll() {
        for (ArcadeMatch match : matches.values()) {
            match.abort();
        }
        matches.clear();
        matchByPlayer.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || matches.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, ArcadeMatch>> it = matches.entrySet().iterator();
        while (it.hasNext()) {
            ArcadeMatch match = it.next().getValue();
            match.tick();
            if (match.isEnded()) {
                matchByPlayer.values().removeIf(mid -> mid.equals(match.matchId()));
                it.remove();
                if (endListener != null) {
                    endListener.accept(match.matchId());
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        String matchId = matchByPlayer.get(victim.getUUID());
        if (matchId == null) {
            return;
        }
        ArcadeMatch match = matches.get(matchId);
        if (match != null) {
            match.onHurt(victim.getUUID());
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        String matchId = matchByPlayer.get(victim.getUUID());
        if (matchId == null) {
            return;
        }
        ArcadeMatch match = matches.get(matchId);
        if (match == null) {
            return;
        }
        UUID killerId = resolveKiller(event);
        // 对局接管死亡：取消原版死亡流程，避免重生换 ServerPlayer 实例导致引用失效。
        if (match.onDeath(victim.getUUID(), killerId)) {
            event.setCanceled(true);
        }
    }

    /**
     * 拾取死亡补给箱：仅对局中玩家生效，补满虚拟弹药后消耗补给箱。
     */
    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        ItemEntity itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItem();
        if (!isAmmoCrate(stack)) {
            return;
        }
        event.setCanceled(true); // 补给箱不进背包
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (!matchByPlayer.containsKey(player.getUUID())) {
            return; // 非参战玩家不能拾取
        }
        int refilled = LoadoutApplier.refillAllGuns(player);
        itemEntity.discard();
        player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.5f);
        player.displayClientMessage(Component.literal(
                refilled > 0 ? "§b补给箱 §7» 弹药已补满" : "§7补给箱：没有可补给的枪"), true);
    }

    private static boolean isAmmoCrate(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(ArcadeMatch.AMMO_CRATE_KEY);
    }

    private UUID resolveKiller(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer killer) {
            return killer.getUUID();
        }
        // 枪械/弓弩等投射物伤害：致伤实体可能是子弹本身，取其拥有者（射手）作为击杀者。
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer shooter) {
            return shooter.getUUID();
        }
        return null;
    }
}
