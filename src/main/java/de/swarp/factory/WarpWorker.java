package de.swarp.factory;

import de.swarp.database.repository.WarpRepository;
import de.swarp.effects.WarpEffectService;
import de.swarp.guice.PluginConfig;
import de.swarp.model.PlayerWarp;
import de.swarp.service.WarpCacheService;
import de.swarp.service.WarpPermissionService;
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

public class WarpWorker implements Runnable {

    private final WarpTask task;
    private final JavaPlugin plugin;
    private final WarpRepository repository;
    private final WarpCacheService cacheService;
    private final WarpEffectService effectService;
    private final PluginConfig config;
    private final ScheduledExecutorService scheduler;
    private final WarpPermissionService permissionService;

    WarpWorker(WarpTask task,
               JavaPlugin plugin,
               WarpRepository repository,
               WarpCacheService cacheService,
               WarpEffectService effectService,
               PluginConfig config,
               ScheduledExecutorService scheduler,
               WarpPermissionService permissionService) {
        this.task = task;
        this.plugin = plugin;
        this.repository = repository;
        this.cacheService = cacheService;
        this.effectService = effectService;
        this.config = config;
        this.scheduler = scheduler;
        this.permissionService = permissionService;
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

                cacheService.updateVisits(warp.withIncrementedVisits());
                CompletableFuture.runAsync(() -> {
                    try { repository.incrementVisits(warp.id()); }
                    catch (SQLException e) {
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

        // Permission-based max warps
        int maxWarps = permissionService.getMaxWarps(player);

        try {
            int owned = repository.countByOwner(player.getUniqueId());
            if (owned >= maxWarps) {
                sendMain(() -> player.sendMessage(
                        Component.text("✗ Du kannst maximal " + maxWarps + " Warps erstellen!", NamedTextColor.RED)));
                return;
            }

            boolean nameExists = repository
                    .findByOwnerAndName(player.getUniqueId(), task.warpName())
                    .isPresent();
            if (nameExists) {
                sendMain(() -> player.sendMessage(
                        Component.text("✗ Du hast bereits einen Warp namens \"" + task.warpName() + "\".", NamedTextColor.RED)));
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
            PlayerWarp saved = PlayerWarp.builder()
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

            cacheService.put(saved);

            sendMain(() -> {
                effectService.playWarpCreatedEffect(player);
                player.sendMessage(
                        Component.text("✔ Warp ", NamedTextColor.GREEN)
                        .append(Component.text("\"" + saved.name() + "\"", NamedTextColor.GOLD))
                        .append(Component.text(" erstellt!", NamedTextColor.GREEN)));
            });

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating warp", e);
            sendMain(() -> player.sendMessage(Component.text("✗ Datenbankfehler. Bitte versuche es später erneut.", NamedTextColor.RED)));
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
                            .append(Component.text(" gelöscht.", NamedTextColor.GREEN)));
                });
            } else {
                sendMain(() -> player.sendMessage(Component.text("✗ Warp konnte nicht gelöscht werden.", NamedTextColor.RED)));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting warp", e);
            sendMain(() -> player.sendMessage(Component.text("✗ Datenbankfehler.", NamedTextColor.RED)));
        }
    }

    private void sendMain(Runnable r) {
        plugin.getServer().getScheduler().runTask(plugin, r);
    }
}
