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
                // Warm cache
                cache.putAll(repository.findByOwner(uuid));
                repository.updateLastSeen(uuid);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to load warps / update last_seen for " + event.getPlayer().getName(), e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        cache.invalidatePlayer(uuid);
        CompletableFuture.runAsync(() -> {
            try { repository.updateLastSeen(uuid); }
            catch (SQLException e) { /* non-critical */ }
        });
    }
}
