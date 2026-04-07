package de.swarp.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.annotations.AsyncQuery;
import de.swarp.database.DatabaseManager;
import de.swarp.model.PlayerWarp;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for {@link PlayerWarp}.
 * All methods MUST be called from an async thread.
 */
@Singleton
public class WarpRepository {

    private static final String INSERT_WARP =
            "INSERT INTO swarp_warps (owner_uuid, owner_name, name, world, x, y, z, yaw, pitch, description, public_warp) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_OWNER =
            "SELECT * FROM swarp_warps WHERE owner_uuid = ?";

    private static final String SELECT_BY_NAME_AND_OWNER =
            "SELECT * FROM swarp_warps WHERE owner_uuid = ? AND name = ?";

    private static final String SELECT_ALL_PUBLIC =
            "SELECT * FROM swarp_warps WHERE public_warp = 1 ORDER BY visits DESC";

    private static final String SELECT_BY_WARP_NAME =
            "SELECT * FROM swarp_warps WHERE name = ? AND public_warp = 1 LIMIT 1";

    private static final String DELETE_BY_ID =
            "DELETE FROM swarp_warps WHERE id = ?";

    private static final String UPDATE_VISITS =
            "UPDATE swarp_warps SET visits = visits + 1 WHERE id = ?";

    private static final String COUNT_BY_OWNER =
            "SELECT COUNT(*) FROM swarp_warps WHERE owner_uuid = ?";

    private final DatabaseManager db;

    @Inject
    public WarpRepository(DatabaseManager db) {
        this.db = db;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Write Operations
    // ──────────────────────────────────────────────────────────────────────────

    @AsyncQuery("Insert a new warp record and return the generated ID")
    public int insert(PlayerWarp warp) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_WARP, Statement.RETURN_GENERATED_KEYS)) {
            Location loc = warp.getLocation();
            ps.setString(1, warp.getOwnerUuid().toString());
            ps.setString(2, warp.getOwnerName());
            ps.setString(3, warp.getName());
            ps.setString(4, loc.getWorld().getName());
            ps.setDouble(5, loc.getX());
            ps.setDouble(6, loc.getY());
            ps.setDouble(7, loc.getZ());
            ps.setFloat(8, loc.getYaw());
            ps.setFloat(9, loc.getPitch());
            ps.setString(10, warp.getDescription());
            ps.setBoolean(11, warp.isPublicWarp());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    @AsyncQuery("Increment visit counter for warp by ID")
    public void incrementVisits(int warpId) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_VISITS)) {
            ps.setInt(1, warpId);
            ps.executeUpdate();
        }
    }

    @AsyncQuery("Delete warp by ID")
    public boolean delete(int warpId) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(DELETE_BY_ID)) {
            ps.setInt(1, warpId);
            return ps.executeUpdate() > 0;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Read Operations
    // ──────────────────────────────────────────────────────────────────────────

    @AsyncQuery("Load all warps belonging to a player")
    public List<PlayerWarp> findByOwner(UUID ownerUuid) throws SQLException {
        List<PlayerWarp> result = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_OWNER)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    @AsyncQuery("Find a specific warp by owner UUID and warp name")
    public Optional<PlayerWarp> findByOwnerAndName(UUID ownerUuid, String name) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_NAME_AND_OWNER)) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @AsyncQuery("Load all public warps sorted by visits descending")
    public List<PlayerWarp> findAllPublic() throws SQLException {
        List<PlayerWarp> result = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL_PUBLIC);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        }
        return result;
    }

    @AsyncQuery("Find first public warp matching a name (global search)")
    public Optional<PlayerWarp> findPublicByName(String name) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_WARP_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @AsyncQuery("Count how many warps a player owns")
    public int countByOwner(UUID ownerUuid) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(COUNT_BY_OWNER)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapping
    // ──────────────────────────────────────────────────────────────────────────

    private PlayerWarp mapRow(ResultSet rs) throws SQLException {
        World world = Bukkit.getWorld(rs.getString("world"));
        Location loc = new Location(
                world,
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch")
        );
        return PlayerWarp.builder()
                .id(rs.getInt("id"))
                .ownerUuid(UUID.fromString(rs.getString("owner_uuid")))
                .ownerName(rs.getString("owner_name"))
                .name(rs.getString("name"))
                .location(loc)
                .description(rs.getString("description"))
                .publicWarp(rs.getBoolean("public_warp"))
                .visits(rs.getLong("visits"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .build();
    }
}
