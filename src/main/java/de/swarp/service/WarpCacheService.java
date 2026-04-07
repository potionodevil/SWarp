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
        byOwner.computeIfAbsent(warp.ownerUuid(), k -> new ConcurrentHashMap<>())
               .put(warp.name().toLowerCase(), warp);

        if (warp.publicWarp()) {
            publicByName.putIfAbsent(warp.name().toLowerCase(), warp);
        }
    }

    public void putAll(Collection<PlayerWarp> warps) {
        warps.forEach(this::put);
    }

    public void remove(PlayerWarp warp) {
        Map<String, PlayerWarp> ownerMap = byOwner.get(warp.ownerUuid());
        if (ownerMap != null) {
            ownerMap.remove(warp.name().toLowerCase());
        }
        publicByName.remove(warp.name().toLowerCase());
    }

    public void updateVisits(PlayerWarp updated) {
        Map<String, PlayerWarp> ownerMap = byOwner.get(updated.ownerUuid());
        if (ownerMap != null) {
            ownerMap.put(updated.name().toLowerCase(), updated);
        }
        if (updated.publicWarp()) {
            publicByName.put(updated.name().toLowerCase(), updated);
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
                    .filter(PlayerWarp::publicWarp)
                    .forEach(w -> publicByName.remove(w.name().toLowerCase()));
        }
    }
}
