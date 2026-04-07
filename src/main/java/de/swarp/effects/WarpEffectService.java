package de.swarp.effects;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.annotations.WarpEffect;
import de.swarp.guice.PluginConfig;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Central service for all warp-related visual and audio effects.
 * Methods are annotated with {@link WarpEffect} for discoverability.
 */
@Singleton
public class WarpEffectService {

    private final JavaPlugin plugin;
    private final PluginConfig config;

    @Inject
    public WarpEffectService(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @WarpEffect(id = "teleport_arrival", displayName = "Teleport Arrival")
    public void playTeleportArrivalEffect(Player player) {
        if (!config.getBoolean("effects.teleport-particles", true)) return;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < 40; i++) {
                double angle = 2 * Math.PI * i / 20.0;
                double radius = 1.2;
                double x = loc.getX() + radius * Math.cos(angle);
                double z = loc.getZ() + radius * Math.sin(angle);
                double y = loc.getY() + (i / 20.0) * 2.5;
                world.spawnParticle(Particle.PORTAL, x, y, z, 3, 0, 0, 0, 0.05);
            }
        }, 1L);

        world.spawnParticle(Particle.INSTANT_EFFECT, loc.add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);

        if (config.getBoolean("effects.sound-on-teleport", true)) {
            player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.8f), 5L);
        }
    }

    @WarpEffect(id = "countdown_tick", displayName = "Countdown Tick")
    public void playCountdownEffect(Player player, int secondsLeft) {
        if (!config.getBoolean("effects.teleport-particles", true)) return;

        Location loc = player.getLocation().add(0, 1, 0);
        World world = loc.getWorld();
        if (world == null) return;
        int count = secondsLeft * 8;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double radius = 0.6 * secondsLeft;
            world.spawnParticle(Particle.END_ROD,
                    loc.getX() + radius * Math.cos(angle),
                    loc.getY(),
                    loc.getZ() + radius * Math.sin(angle),
                    1, 0, 0, 0, 0.01);
        }

        player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING,
                0.6f, 1.0f + (0.2f * (4 - secondsLeft)));
    }


    @WarpEffect(id = "warp_created", displayName = "Warp Created")
    public void playWarpCreatedEffect(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);

        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16.0;
            world.spawnParticle(Particle.END_ROD,
                    loc.getX() + Math.cos(angle),
                    loc.getY() + 0.1,
                    loc.getZ() + Math.sin(angle),
                    2, 0, 0.1, 0, 0.02);
        }

        if (config.getBoolean("effects.sound-on-set", true)) {
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
        }
    }

    @WarpEffect(id = "warp_deleted", displayName = "Warp Deleted")
    public void playWarpDeletedEffect(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.LARGE_SMOKE, loc, 25, 0.3, 0.5, 0.3, 0.04);
        world.spawnParticle(Particle.ASH, loc, 15, 0.5, 0.5, 0.5, 0.01);

        player.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.5f, 0.8f);
    }
}
