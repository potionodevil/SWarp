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
import java.util.UUID;
import java.util.logging.Level;

/**
 * Scheduled worker that removes warps belonging to players
 * who have been offline for longer than {@code warps.expire-days} days.
 *
 * Runs once at startup and then every 24h via Bukkit's async scheduler.
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

    /**
     * Schedules this worker to run every 24h asynchronously.
     * Call once from {@code SwarpPlugin#onEnable}.
     */
    public void schedule() {
        long intervalTicks = 20L * 60 * 60 * 24;
        plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this, 20L * 60, intervalTicks);
    }

    @Override
    public void run() {
        int expireDays = config.getInt("warps.expire-days", 30);
        if (expireDays <= 0) return; // 0 = disabled

        try {
            List<UUID> expiredOwners = repository.findExpiredOwners(expireDays);
            if (expiredOwners.isEmpty()) return;

            int deleted = 0;
            for (UUID uuid : expiredOwners) {
                List<PlayerWarp> warps = repository.findByOwner(uuid);
                for (PlayerWarp warp : warps) {
                    if (repository.delete(warp.id())) {
                        cache.remove(warp);
                        deleted++;
                    }
                }
            }

            if (deleted > 0) {
                plugin.getLogger().info("[SWarp] Expire worker removed " + deleted
                        + " warp(s) from " + expiredOwners.size() + " inactive player(s).");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SWarp] Expire worker failed", e);
        }
    }
}
