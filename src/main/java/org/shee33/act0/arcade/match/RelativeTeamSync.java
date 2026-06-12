package org.shee33.act0.arcade.match;

import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

/** 按观察者视角同步虚拟队伍：友方蓝色，敌方红色。 */
public final class RelativeTeamSync {
    private RelativeTeamSync() {
    }

    public static void sync(ServerPlayer viewer, Collection<UUID> players,
                            Function<UUID, ServerPlayer> playerResolver,
                            Function<UUID, Boolean> friendlyResolver) {
        if (viewer == null || players == null) {
            return;
        }
        Scoreboard board = new Scoreboard();
        PlayerTeam friendly = board.addPlayerTeam(teamName(viewer, true));
        friendly.setColor(ChatFormatting.BLUE);
        friendly.setNameTagVisibility(Team.Visibility.ALWAYS);
        PlayerTeam enemy = board.addPlayerTeam(teamName(viewer, false));
        enemy.setColor(ChatFormatting.RED);
        enemy.setNameTagVisibility(Team.Visibility.ALWAYS);

        viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(friendly, true));
        viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(enemy, true));

        for (UUID id : players) {
            ServerPlayer target = playerResolver.apply(id);
            if (target == null) {
                continue;
            }
            boolean isFriendly = Boolean.TRUE.equals(friendlyResolver.apply(id));
            PlayerTeam team = isFriendly ? friendly : enemy;
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, target.getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
    }

    private static String teamName(ServerPlayer viewer, boolean friendly) {
        String base = "a0" + Integer.toHexString(viewer.getUUID().hashCode()) + (friendly ? "F" : "E");
        return base.length() <= 16 ? base : base.substring(0, 16);
    }
}
