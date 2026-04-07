package de.swarp.factory;

import de.swarp.database.repository.WarpRepository;
import de.swarp.effects.WarpEffectService;
import de.swarp.guice.PluginConfig;
import de.swarp.model.PlayerWarp;
import de.swarp.service.WarpCacheService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Executes one {@link WarpTask}.
 * Workers are created by {@link WarpWorkerFactory} and are NOT singletons —
 * each task gets its own worker instance (Factory-Worker pattern).
 *
 * Flow for TELEPORT:
 *  1. Async: validate player / warp still exist
 *  2. Async: show countdown titles via scheduled executor
 *  3. Back on main thread: teleport + play effects
 *  4. Async: increment visit counter in DB
 */
public class WarpWorker implements Runnable {

    private final WarpTask task;
    private final JavaPlugin plugin;
    private final WarpRepository repository;
    private final WarpCacheService cacheService;
    private final WarpEffectService effectService;
    private final PluginConfig config;
    private final ScheduledExecutorService scheduler;

    WarpWorker(WarpTask task,
               JavaPlugin plugin,
               WarpRepository repository,
               WarpCacheService cacheService,
               WarpEffectService effectService,
               PluginConfig config,
               ScheduledExecutorService scheduler) {
        this.task = task;
        this.plugin = plugin;
        this.repository = repository;
        this.cacheService = cacheService;
        this.effectService = effectService;
        this.config = config;
        this.scheduler = scheduler;
    }

    @Override
    public void run() {
        switch (task.type()) {
            case TELEPORT -> handleTeleport();
            case CREATE   -> handleCreate();
            case DELETE   -> handleDelete();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Teleport
    // ──────────────────────────────────────────────────────────────────────────

    private void handleTeleport() {
        Player player = task.player();
        if (!player.isOnline()) return;

        PlayerWarp warp = task.targetWarp();
        int delay = config.getInt("warps.teleport-delay", 3);

        // Countdown titles
        for (int i = delay; i > 0; i--) {
            final int countdown = i;
            scheduler.schedule(() -> {
                if (!player.isOnline()) return;
                player.showTitle(Title.title(
                        Component.text("✦ TELEPORTING ✦", NamedTextColor.GOLD),
                        Component.text("in " + countdown + " second" + (countdown != 1 ? "s" : "") + "…", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(200))
                ));
                effectService.playCountdownEffect(player, countdown);
            }, (long)(delay - i) * 1000L, TimeUnit.MILLISECONDS);
        }

        // Teleport after delay (must run on main thread)
        scheduler.schedule(() ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                Location dest = warp.location();
                player.teleport(dest);

                player.showTitle(Title.title(
                        Component.text("✦ " + warp.name().toUpperCase() + " ✦", NamedTextColor.GOLD),
                        Component.text("by " + warp.ownerName(), NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(500))
                ));

                effectService.playTeleportArrivalEffect(player);

                // Update cache & DB visit counter async
                cacheService.updateVisits(warp.withIncrementedVisits());
                CompletableFuture.runAsync(() -> {
                    try {
                        repository.incrementVisits(warp.id());
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to increment visits for warp " + warp.id(), e);
                    }
                });
            }),
        (long) delay * 1000L, TimeUnit.MILLISECONDS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────────

    private void handleCreate() {
        Player player = task.player();
        if (!player.isOnline()) return;

        int maxWarps = config.getInt("warps.max-per-player", 3);

        try {
            int owned = repository.countByOwner(player.getUniqueId());
            if (owned >= maxWarps) {
                sendMain(() -> player.sendMessage(
                        Component.text("✗ You can only have " + maxWarps + " warps!", NamedTextColor.RED)));
                return;
            }

            boolean nameExists = repository
                    .findByOwnerAndName(player.getUniqueId(), task.warpName())
                    .isPresent();
            if (nameExists) {
                sendMain(() -> player.sendMessage(
                        Component.text("✗ You already have a warp named \"" + task.warpName() + "\".", NamedTextColor.RED)));
                return;
            }

            PlayerWarp newWarp = PlayerWarp.builder()
                    .id(-1)
                    .ownerUuid(player.getUniqueId())
                    .ownerName(player.getName())
                    .name(task.warpName())
                    .location(player.getLocation().clone())
                    .description("")
                    .publicWarp(true)
                    .visits(0)
                    .createdAt(java.time.Instant.now())
                    .build();

            int generatedId = repository.insert(newWarp);
            PlayerWarp savedWarp = PlayerWarp.builder()
                    .id(generatedId)
                    .ownerUuid(newWarp.ownerUuid())
                    .ownerName(newWarp.ownerName())
                    .name(newWarp.name())
                    .location(newWarp.location())
                    .description(newWarp.description())
                    .publicWarp(newWarp.publicWarp())
                    .visits(0)
                    .createdAt(newWarp.createdAt())
                    .build();

            cacheService.put(savedWarp);

            sendMain(() -> {
                effectService.playWarpCreatedEffect(player);
                player.sendMessage(
                        Component.text("✔ Warp ", NamedTextColor.GREEN)
                        .append(Component.text("\"" + savedWarp.name() + "\"", NamedTextColor.GOLD))
                        .append(Component.text(" created!", NamedTextColor.GREEN)));
            });

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating warp", e);
            sendMain(() -> player.sendMessage(Component.text("✗ Database error. Please try again later.", NamedTextColor.RED)));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────────

    private void handleDelete() {
        Player player = task.player();
        if (!player.isOnline()) return;

        PlayerWarp warp = task.targetWarp();
        try {
            boolean deleted = repository.delete(warp.id());
            if (deleted) {
                cacheService.remove(warp);
                sendMain(() -> {
                    effectService.playWarpDeletedEffect(player);
                    player.sendMessage(
                            Component.text("✔ Warp ", NamedTextColor.GREEN)
                            .append(Component.text("\"" + warp.name() + "\"", NamedTextColor.GOLD))
                            .append(Component.text(" deleted.", NamedTextColor.GREEN)));
                });
            } else {
                sendMain(() -> player.sendMessage(Component.text("✗ Could not delete warp — not found.", NamedTextColor.RED)));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting warp", e);
            sendMain(() -> player.sendMessage(Component.text("✗ Database error. Please try again.", NamedTextColor.RED)));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    private void sendMain(Runnable r) {
        plugin.getServer().getScheduler().runTask(plugin, r);
    }
}
