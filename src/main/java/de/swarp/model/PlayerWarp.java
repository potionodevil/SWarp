package de.swarp.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain model representing a single player-owned warp.
 *
 * Declared as a record: the compiler auto-generates the canonical constructor,
 * accessors (id(), ownerUuid(), …), equals(), hashCode(), and toString().
 * No Lombok needed.
 */
public record PlayerWarp(
        /** Primary key in DB; -1 before first persist */
        int id,
        UUID ownerUuid,
        String ownerName,
        String name,
        Location location,
        String description,
        boolean publicWarp,
        long visits,
        Instant createdAt
) {

    /**
     * Returns a copy of this record with the visit count incremented by 1.
     * Records are immutable, so we construct a new instance.
     */
    public PlayerWarp withIncrementedVisits() {
        return new PlayerWarp(id, ownerUuid, ownerName, name, location,
                description, publicWarp, visits + 1, createdAt);
    }

    /**
     * Fluent builder for readable construction sites (e.g. in WarpRepository).
     */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int id = -1;
        private UUID ownerUuid;
        private String ownerName;
        private String name;
        private Location location;
        private String description = "";
        private boolean publicWarp = true;
        private long visits = 0;
        private Instant createdAt = Instant.now();

        public Builder id(int id)                   { this.id = id;                 return this; }
        public Builder ownerUuid(UUID v)            { this.ownerUuid = v;           return this; }
        public Builder ownerName(String v)          { this.ownerName = v;           return this; }
        public Builder name(String v)               { this.name = v;                return this; }
        public Builder location(Location v)         { this.location = v;            return this; }
        public Builder description(String v)        { this.description = v;         return this; }
        public Builder publicWarp(boolean v)        { this.publicWarp = v;          return this; }
        public Builder visits(long v)               { this.visits = v;              return this; }
        public Builder createdAt(Instant v)         { this.createdAt = v;           return this; }

        public PlayerWarp build() {
            return new PlayerWarp(id, ownerUuid, ownerName, name, location,
                    description, publicWarp, visits, createdAt);
        }
    }
}
