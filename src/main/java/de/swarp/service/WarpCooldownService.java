package de.swarp.service;

import com.google.inject.Singleton;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player teleport cooldowns.
 * Stored in memory only — resets on server restart (intentional).
 */
@Singleton
public class WarpCooldownService {

    private final Map<UUID, Long> lastTeleport = new ConcurrentHashMap<>();

    /**
     * Returns remaining cooldown in seconds, or 0 if the player can teleport.
     */
    public int getRemainingCooldown(UUID uuid, int cooldownSeconds) {
        Long last = lastTeleport.get(uuid);
        if (last == null) return 0;

        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        int remaining = (int) (cooldownSeconds - elapsed);
        return Math.max(remaining, 0);
    }

    /**
     * Returns true if the player is allowed to teleport right now.
     */
    public boolean canTeleport(UUID uuid, int cooldownSeconds) {
        return getRemainingCooldown(uuid, cooldownSeconds) == 0;
    }

    /**
     * Marks the player as having just teleported.
     */
    public void registerTeleport(UUID uuid) {
        lastTeleport.put(uuid, System.currentTimeMillis());
    }

    public void clearPlayer(UUID uuid) {
        lastTeleport.remove(uuid);
    }
}
