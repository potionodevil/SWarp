package de.swarp.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.UUID;

public record PlayerWarp(
        int id,
        UUID ownerUuid,
        String ownerName,
        String name,
        Location location,
        String description,
        boolean publicWarp,
        long visits,
        Instant createdAt,
        WarpCategory category,
        boolean expires
) {

    public PlayerWarp withIncrementedVisits() {
        return new PlayerWarp(id, ownerUuid, ownerName, name, location,
                description, publicWarp, visits + 1, createdAt, category, expires);
    }

    public PlayerWarp withDescription(String newDesc) {
        return new PlayerWarp(id, ownerUuid, ownerName, name, location,
                newDesc, publicWarp, visits, createdAt, category, expires);
    }

    public PlayerWarp withCategory(WarpCategory cat) {
        return new PlayerWarp(id, ownerUuid, ownerName, name, location,
                description, publicWarp, visits, createdAt, cat, expires);
    }

    public PlayerWarp withName(String newName) {
        return new PlayerWarp(id, ownerUuid, ownerName, newName, location,
                description, publicWarp, visits, createdAt, category, expires);
    }

    public PlayerWarp withExpires(boolean expires) {
        return new PlayerWarp(id, ownerUuid, ownerName, name, location,
                description, publicWarp, visits, createdAt, category, expires);
    }

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
        private WarpCategory category = WarpCategory.OTHER;
        private boolean expires = false;

        public Builder id(int v)                { this.id = v;          return this; }
        public Builder ownerUuid(UUID v)        { this.ownerUuid = v;   return this; }
        public Builder ownerName(String v)      { this.ownerName = v;   return this; }
        public Builder name(String v)           { this.name = v;        return this; }
        public Builder location(Location v)     { this.location = v;    return this; }
        public Builder description(String v)    { this.description = v; return this; }
        public Builder publicWarp(boolean v)    { this.publicWarp = v;  return this; }
        public Builder visits(long v)           { this.visits = v;      return this; }
        public Builder createdAt(Instant v)     { this.createdAt = v;   return this; }
        public Builder category(WarpCategory v) { this.category = v;    return this; }
        public Builder expires(boolean v)       { this.expires = v;     return this; }

        public PlayerWarp build() {
            return new PlayerWarp(id, ownerUuid, ownerName, name, location,
                    description, publicWarp, visits, createdAt, category, expires);
        }
    }
}
