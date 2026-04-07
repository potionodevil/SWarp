package de.swarp.factory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.database.repository.WarpRepository;
import de.swarp.guice.PluginConfig;
import de.swarp.model.PlayerWarp;
import de.swarp.service.WarpCacheService;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

/**
 * Runs every 24h and deletes warps where:
 *   - The player explicitly enabled expires = true
 *   - AND the player has been offline for longer than expire-days
 *
 * Warps with expires = false are NEVER touched.
 */
@Singleton
public class WarpExpireWorker implements Runnable {

    private final JavaPlugin plugin;
    private final WarpRepository repository;
    private final WarpCacheService cache;
    private final PluginConfig config;

    @Inject
    public WarpExpireWorker(JavaPlugin plugin,
                            WarpRepository repository,
                            WarpCacheService cache,
                            PluginConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.cache = cache;
        this.config = config;
    }

    public void schedule() {
        long intervalTicks = 20L * 60 * 60 * 24;
        plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this, 20L * 60, intervalTicks);
    }

    @Override
    public void run() {
        int expireDays = config.getInt("warps.expire-days", 30);
        if (expireDays <= 0) return;

        try {
            List<PlayerWarp> expired = repository.findExpiredWarps(expireDays);
            if (expired.isEmpty()) return;

            int deleted = 0;
            for (PlayerWarp warp : expired) {
                if (repository.delete(warp.id())) {
                    cache.remove(warp);
                    deleted++;
                }
            }

            plugin.getLogger().info("[SWarp] Expire worker: " + deleted + " abgelaufene Warp(s) gelöscht.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SWarp] Expire worker fehlgeschlagen", e);
        }
    }
}
