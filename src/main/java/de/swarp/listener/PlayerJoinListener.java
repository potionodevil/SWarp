package de.swarp.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.database.repository.WarpRepository;
import de.swarp.service.WarpCacheService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Warms the per-player warp cache when a player joins
 * and cleans it up on quit.
 */
@Singleton
public class PlayerJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final WarpRepository repository;
    private final WarpCacheService cache;

    @Inject
    public PlayerJoinListener(JavaPlugin plugin,
                               WarpRepository repository,
                               WarpCacheService cache) {
        this.plugin = plugin;
        this.repository = repository;
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        CompletableFuture.runAsync(() -> {
            try {
                var warps = repository.findByOwner(uuid);
                cache.putAll(warps);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to load warps for " + event.getPlayer().getName(), e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.invalidatePlayer(event.getPlayer().getUniqueId());
    }
}
