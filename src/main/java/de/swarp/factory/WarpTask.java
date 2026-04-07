package de.swarp.factory;

import de.swarp.model.PlayerWarp;
import lombok.Builder;
import lombok.Data;
import org.bukkit.entity.Player;

/**
 * Immutable value object describing a single teleport job.
 * Passed from the factory to the worker for processing.
 */
@Data
@Builder
public class WarpTask {

    public enum Type {
        TELEPORT,
        CREATE,
        DELETE
    }

    private final Type type;
    private final Player player;
    private final PlayerWarp targetWarp;   // null for CREATE tasks before persistence
    private final String warpName;         // used for CREATE / lookup
    private final long createdAt;

    public static WarpTask teleport(Player player, PlayerWarp warp) {
        return WarpTask.builder()
                .type(Type.TELEPORT)
                .player(player)
                .targetWarp(warp)
                .warpName(warp.getName())
                .createdAt(System.currentTimeMillis())
                .build();
    }

    public static WarpTask create(Player player, String name) {
        return WarpTask.builder()
                .type(Type.CREATE)
                .player(player)
                .warpName(name)
                .createdAt(System.currentTimeMillis())
                .build();
    }

    public static WarpTask delete(Player player, PlayerWarp warp) {
        return WarpTask.builder()
                .type(Type.DELETE)
                .player(player)
                .targetWarp(warp)
                .warpName(warp.getName())
                .createdAt(System.currentTimeMillis())
                .build();
    }
}
