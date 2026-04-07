package de.swarp.factory;

import de.swarp.model.PlayerWarp;
import org.bukkit.entity.Player;

/**
 * Immutable value object describing a single warp job.
 * Passed from the factory to the worker for processing.
 */
public record WarpTask(
        Type type,
        Player player,
        PlayerWarp targetWarp,   // null for CREATE tasks before persistence
        String warpName,
        long createdAt
) {
    public enum Type { TELEPORT, CREATE, DELETE }

    public static WarpTask teleport(Player player, PlayerWarp warp) {
        return new WarpTask(Type.TELEPORT, player, warp, warp.name(), System.currentTimeMillis());
    }

    public static WarpTask create(Player player, String name) {
        return new WarpTask(Type.CREATE, player, null, name, System.currentTimeMillis());
    }

    public static WarpTask delete(Player player, PlayerWarp warp) {
        return new WarpTask(Type.DELETE, player, warp, warp.name(), System.currentTimeMillis());
    }
}
