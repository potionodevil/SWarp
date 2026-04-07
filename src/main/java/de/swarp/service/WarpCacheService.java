package de.swarp.service;

import com.google.inject.Singleton;
import de.swarp.model.PlayerWarp;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache.
 *
 * Cache structure:
 *   ownerUuid → ( warpName → PlayerWarp )
 *
 * Public warps are additionally indexed in a flat name map for fast
 * global lookups (e.g. /swarp tp <name>).
 */
@Singleton
public class WarpCacheService {
    private final ConcurrentHashMap<UUID, Map<String, PlayerWarp>> byOwner = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlayerWarp> publicByName = new ConcurrentHashMap<>();

    public void put(PlayerWarp warp) {
        byOwner.computeIfAbsent(warp.getOwnerUuid(), k -> new ConcurrentHashMap<>())
               .put(warp.getName().toLowerCase(), warp);

        if (warp.isPublicWarp()) {
            publicByName.putIfAbsent(warp.getName().toLowerCase(), warp);
        }
    }

    public void putAll(Collection<PlayerWarp> warps) {
        warps.forEach(this::put);
    }

    public void remove(PlayerWarp warp) {
        Map<String, PlayerWarp> ownerMap = byOwner.get(warp.getOwnerUuid());
        if (ownerMap != null) {
            ownerMap.remove(warp.getName().toLowerCase());
        }
        publicByName.remove(warp.getName().toLowerCase());
    }

    public void updateVisits(PlayerWarp updated) {
        Map<String, PlayerWarp> ownerMap = byOwner.get(updated.getOwnerUuid());
        if (ownerMap != null) {
            ownerMap.put(updated.getName().toLowerCase(), updated);
        }
        if (updated.isPublicWarp()) {
            publicByName.put(updated.getName().toLowerCase(), updated);
        }
    }

    public Optional<PlayerWarp> getByOwnerAndName(UUID ownerUuid, String name) {
        Map<String, PlayerWarp> ownerMap = byOwner.get(ownerUuid);
        return ownerMap == null
                ? Optional.empty()
                : Optional.ofNullable(ownerMap.get(name.toLowerCase()));
    }

    public Optional<PlayerWarp> getPublicByName(String name) {
        return Optional.ofNullable(publicByName.get(name.toLowerCase()));
    }

    public List<PlayerWarp> getByOwner(UUID ownerUuid) {
        Map<String, PlayerWarp> ownerMap = byOwner.get(ownerUuid);
        return ownerMap == null ? Collections.emptyList() : new ArrayList<>(ownerMap.values());
    }

    public List<PlayerWarp> getAllPublic() {
        return new ArrayList<>(publicByName.values());
    }

    public void invalidatePlayer(UUID ownerUuid) {
        Map<String, PlayerWarp> removed = byOwner.remove(ownerUuid);
        if (removed != null) {
            removed.values().stream()
                    .filter(PlayerWarp::isPublicWarp)
                    .forEach(w -> publicByName.remove(w.getName().toLowerCase()));
        }
    }
}
