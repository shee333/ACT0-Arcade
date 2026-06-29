package org.shee33.act0.arcade.match;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 匹配队列：玩家按 (模式 + 竞技场) 排队，凑够人数即由 {@link MatchLauncher} 自动开局，
 * 免去管理员手动 {@code /arcade start}。
 *
 * <p>每个 (模式, 竞技场) 组合是一条独立队列。人数门槛见 {@link #targetSize(String)}。
 * 所有方法在服务器主线程调用，非线程安全。
 */
public final class MatchQueue {

    /** 队列键 → 排队玩家（保持加入顺序）。 */
    private final Map<String, Set<UUID>> queues = new LinkedHashMap<>();
    /** 玩家 → 所在队列键，保证一名玩家只在一条队列。 */
    private final Map<UUID, String> queueOf = new LinkedHashMap<>();

    private final ArcadeServices services;

    public MatchQueue(ArcadeServices services) {
        this.services = services;
    }

    /** 某模式自动开局所需的排队人数门槛。 */
    public static int targetSize(String mode) {
        return switch (mode) {
            case "duel_1v1" -> 2;
            case "duel_2v2" -> 4;
            case "team_deathmatch", "hot_zone" -> 4;
            case "free_for_all", "jump_sniper" -> 4;
            default -> -1;
        };
    }

    private static String key(String mode, String arenaId) {
        return mode + "@" + arenaId;
    }

    /**
     * 玩家加入 (模式, 竞技场) 队列。若已在对局或其它队列，会先给出提示并拒绝/转移。
     * 凑够门槛时立即开局。
     *
     * @return 面向发起者的反馈文本
     */
    public String enqueue(MinecraftServer server, ServerPlayer player, String mode, String arenaId) {
        int target = targetSize(mode);
        if (target <= 0) {
            return "§c未知模式：" + mode;
        }
        UUID id = player.getUUID();
        if (services.matches().isInMatch(id)) {
            return "§c你已在对局中，无法排队。";
        }
        String existing = queueOf.get(id);
        String newKey = key(mode, arenaId);
        if (newKey.equals(existing)) {
            return "§e你已在该队列中（" + queueSize(newKey) + "/" + target + "）。";
        }
        if (existing != null) {
            removeFromQueue(id);
        }

        Set<UUID> queue = queues.computeIfAbsent(newKey, k -> new LinkedHashSet<>());
        queue.add(id);
        queueOf.put(id, newKey);

        broadcastQueue(server, newKey, "§b" + player.getGameProfile().getName()
                + " §7加入队列 §e" + mode + "§7@§e" + arenaId
                + " §7(" + queue.size() + "/" + target + ")");

        tryLaunch(server, newKey, mode, arenaId, target);
        return "§a已加入队列 §e" + mode + " §7@ §e" + arenaId
                + " §7(" + queueSize(newKey) + "/" + target + ")";
    }

    /** 玩家主动离队。 */
    public String leave(ServerPlayer player) {
        if (removeFromQueue(player.getUUID()) != null) {
            return "§a已离开匹配队列。";
        }
        return "§7你当前不在任何队列。";
    }

    /** 玩家登出时调用，清除其排队状态。 */
    public void onPlayerLogout(UUID id) {
        removeFromQueue(id);
    }

    /** 队列状态摘要（面向命令输出）。 */
    public String status() {
        if (queues.isEmpty()) {
            return "§7当前没有任何排队。";
        }
        StringBuilder sb = new StringBuilder("§e匹配队列：");
        for (Map.Entry<String, Set<UUID>> e : queues.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            String mode = e.getKey().substring(0, e.getKey().indexOf('@'));
            sb.append("\n §7- §f").append(e.getKey())
                    .append(" §7(").append(e.getValue().size())
                    .append("/").append(targetSize(mode)).append(")");
        }
        return sb.toString();
    }

    private void tryLaunch(MinecraftServer server, String queueKey, String mode, String arenaId, int target) {
        Set<UUID> queue = queues.get(queueKey);
        if (queue == null || queue.size() < target) {
            return;
        }
        // 取出队首 target 名仍在线的玩家
        List<UUID> picked = new ArrayList<>();
        for (UUID id : new ArrayList<>(queue)) {
            if (server.getPlayerList().getPlayer(id) != null) {
                picked.add(id);
            } else {
                removeFromQueue(id); // 顺手清理离线者
            }
            if (picked.size() == target) {
                break;
            }
        }
        if (picked.size() < target) {
            return;
        }
        for (UUID id : picked) {
            removeFromQueue(id);
        }

        MatchLauncher.Result result = MatchLauncher.start(services, server, mode, arenaId, picked);
        if (result.isOk()) {
            return;
        }
        // 开局失败（如竞技场不达标）：通知被取出的玩家，避免静默丢失
        Component msg = Component.literal("§c自动开局失败：" + result.message() + "，请稍后重试。");
        for (UUID id : picked) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(msg);
            }
        }
    }

    private String removeFromQueue(UUID id) {
        String key = queueOf.remove(id);
        if (key == null) {
            return null;
        }
        Set<UUID> queue = queues.get(key);
        if (queue != null) {
            queue.remove(id);
            if (queue.isEmpty()) {
                queues.remove(key);
            }
        }
        return key;
    }

    private int queueSize(String key) {
        Set<UUID> q = queues.get(key);
        return q != null ? q.size() : 0;
    }

    private void broadcastQueue(MinecraftServer server, String key, String message) {
        Set<UUID> queue = queues.get(key);
        if (queue == null) {
            return;
        }
        Component component = Component.literal(message);
        for (UUID id : queue) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(component);
            }
        }
    }
}
