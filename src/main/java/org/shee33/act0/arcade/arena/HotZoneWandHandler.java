package org.shee33.act0.arcade.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.storage.ArenaRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 热区框选道具：管理员执行 {@code /arcade arena hotzonewand <id>} 后手持一件带标记 NBT 的骨头，
 * 左键点方块记录角 1、右键点方块记录角 2，两角都记录后自动 min/max 归一化生成 {@link HotZoneArea}
 * 并写回该竞技场——取代旧版"热区点复用 {@code randomSpawns} + 索引轮转"的机制。
 *
 * <p>复用 {@link Items#BONE}（骨头在正常对局中几乎不会被当作道具使用）+ 显示名 + NBT 标记来识别
 * "这是热区框选工具"，不必为此注册全新 Item 类。会话状态（玩家 → 正在编辑哪个竞技场、两次点击
 * 记录）存于本类持有的内存 map，随服务器重启清空，符合"一次性管理员操作"的语义。
 */
public final class HotZoneWandHandler {

    public static final HotZoneWandHandler INSTANCE = new HotZoneWandHandler();

    /** 道具标记 NBT 键：值恒为 {@code true}，仅用于识别"这是热区框选工具"。 */
    private static final String WAND_TAG = "Act0HotZoneWand";

    private final Map<UUID, String> editingArena = new HashMap<>();
    private final Map<UUID, Corner> corner1 = new HashMap<>();
    private final Map<UUID, Corner> corner2 = new HashMap<>();

    private HotZoneWandHandler() {
    }

    private record Corner(String dimension, BlockPos pos) {
    }

    /** 制作一件带标记 NBT 的热区框选道具。 */
    public static ItemStack createWand() {
        ItemStack stack = new ItemStack(Items.BONE);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(WAND_TAG, true);
        stack.setHoverName(Component.literal("§6热区框选道具"));
        return stack;
    }

    private static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(WAND_TAG);
    }

    /** 开始一次热区编辑会话：清空该玩家此前未完成的记录。 */
    public void beginEditing(UUID playerId, String arenaId) {
        editingArena.put(playerId, arenaId);
        corner1.remove(playerId);
        corner2.remove(playerId);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        handleClick(event, true);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handleClick(event, false);
    }

    private void handleClick(PlayerInteractEvent event, boolean isLeftClick) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isWand(player.getItemInHand(event.getHand()))) {
            return;
        }
        UUID id = player.getUUID();
        String arenaId = editingArena.get(id);
        if (arenaId == null) {
            return;
        }
        event.setCanceled(true);
        BlockPos pos = event.getPos();
        String dimension = event.getLevel().dimension().location().toString();
        Corner corner = new Corner(dimension, pos);
        if (isLeftClick) {
            corner1.put(id, corner);
            player.sendSystemMessage(Component.literal("§a角1已记录：§f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                    + (corner2.containsKey(id) ? "" : " §7请右键点击另一角完成设置。")));
        } else {
            corner2.put(id, corner);
            player.sendSystemMessage(Component.literal("§a角2已记录：§f(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                    + (corner1.containsKey(id) ? "" : " §7请左键点击另一角完成设置。")));
        }
        tryFinish(player, arenaId);
    }

    private void tryFinish(ServerPlayer player, String arenaId) {
        UUID id = player.getUUID();
        Corner c1 = corner1.get(id);
        Corner c2 = corner2.get(id);
        if (c1 == null || c2 == null) {
            return;
        }
        if (!c1.dimension().equals(c2.dimension())) {
            player.sendSystemMessage(Component.literal("§c两个角必须在同一维度，已重置，请重新点击。"));
            corner1.remove(id);
            corner2.remove(id);
            return;
        }
        editingArena.remove(id);
        corner1.remove(id);
        corner2.remove(id);

        ArenaRegistry registry = ArenaRegistry.get(player.getServer());
        Optional<ArcadeArena> existing = registry.find(arenaId);
        if (existing.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c竞技场不存在，热区设置已取消：" + arenaId));
            return;
        }
        HotZoneArea area = HotZoneArea.fromClicks(c1.dimension(),
                c1.pos().getX() + 0.5, c1.pos().getY(), c1.pos().getZ() + 0.5,
                c2.pos().getX() + 0.5, c2.pos().getY(), c2.pos().getZ() + 0.5);
        ArcadeArena old = existing.get();
        ArcadeArena.Builder builder = new ArcadeArena.Builder(arenaId)
                .returnSpawn(old.returnSpawn())
                .hotZone(area);
        old.sideSpawns().forEach(builder::addSideSpawn);
        old.randomSpawns().forEach(builder::addRandomSpawn);
        registry.put(builder.build());

        player.sendSystemMessage(Component.literal("§a已为 §e" + arenaId + " §a设置热区：§f("
                + round(area.minX()) + "~" + round(area.maxX()) + ", " + round(area.minZ()) + "~" + round(area.maxZ()) + ")"));
        ArcadeNetwork.sendHotZoneArea(player, area);
    }

    private static long round(double v) {
        return Math.round(v);
    }
}
