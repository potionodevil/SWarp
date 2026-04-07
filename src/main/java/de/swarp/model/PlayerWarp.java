package de.swarp.model;

import lombok.Builder;
import lombok.Data;
import org.bukkit.Location;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain model representing a single player-owned warp.
 */
@Data
@Builder
public class PlayerWarp {

    /** Unique warp ID (primary key in DB) */
    private final int id;

    /** Owner's UUID */
    private final UUID ownerUuid;

    /** Owner's name at time of creation (cached for display) */
    private final String ownerName;

    /** Unique warp name chosen by the player */
    private final String name;

    /** The teleport destination */
    private final Location location;

    /** Optional short description of the warp (e.g. "Diamond Shop") */
    private final String description;

    /** Whether the warp is publicly visible */
    private final boolean publicWarp;

    /** Visit counter — incremented on every successful teleport */
    private final long visits;

    /** When the warp was originally created */
    private final Instant createdAt;

    /**
     * Returns a copy of this warp with the visit count incremented by 1.
     */
    public PlayerWarp withIncrementedVisits() {
        return PlayerWarp.builder()
                .id(id)
                .ownerUuid(ownerUuid)
                .ownerName(ownerName)
                .name(name)
                .location(location)
                .description(description)
                .publicWarp(publicWarp)
                .visits(visits + 1)
                .createdAt(createdAt)
                .build();
    }
}
