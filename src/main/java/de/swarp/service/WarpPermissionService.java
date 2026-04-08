package de.swarp.service;

import com.google.inject.Singleton;
import de.swarp.guice.PluginConfig;
import com.google.inject.Inject;
import org.bukkit.entity.Player;

/**
 * Resolves the maximum number of warps a player is allowed to create.
 *
 * Permission format: nitro.swarps.create.<anzahl>
 * Example: nitro.swarps.create.5  → player may create 5 warps
 *
 * The highest matching permission wins.
 * Falls back to config value warps.max-per-player if no permission matches.
 */
@Singleton
public class WarpPermissionService {

    private static final String PERMISSION_PREFIX = "nitro.swarps.create.";
    private static final int MAX_CHECK = 100; // upper bound to prevent infinite loop

    private final PluginConfig config;

    @Inject
    public WarpPermissionService(PluginConfig config) {
        this.config = config;
    }

    /**
     * Returns the maximum warp count allowed for this player.
     */
    public int getMaxWarps(Player player) {
        if (player.hasPermission("swarp.admin")) return Integer.MAX_VALUE;

        int highest = -1;
        for (int i = 1; i <= MAX_CHECK; i++) {
            if (player.hasPermission(PERMISSION_PREFIX + i)) {
                highest = i;
            }
        }

        if (highest > 0) return highest;

        // Fallback to config
        return config.getInt("warps.max-per-player", 3);
    }
}
