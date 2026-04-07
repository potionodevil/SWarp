package de.swarp.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.annotations.AsyncQuery;
import de.swarp.database.DatabaseManager;
import de.swarp.model.PlayerWarp;
import de.swarp.model.WarpCategory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class WarpRepository {

    private static final String INSERT_WARP =
            "INSERT INTO swarp_warps (owner_uuid, owner_name, name, world, x, y, z, yaw, pitch, description, public_warp, category, expires) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_OWNER =
            "SELECT * FROM swarp_warps WHERE owner_uuid = ?";

    private static final String SELECT_BY_NAME_AND_OWNER =
            "SELECT * FROM swarp_warps WHERE owner_uuid = ? AND name = ?";

    private static final String SELECT_ALL_PUBLIC =
            "SELECT * FROM swarp_warps WHERE public_warp = 1 ORDER BY visits DESC";

    private static final String SELECT_BY_CATEGORY =
            "SELECT * FROM swarp_warps WHERE public_warp = 1 AND category = ? ORDER BY visits DESC";

    private static final String SELECT_SEARCH =
            "SELECT * FROM swarp_warps WHERE public_warp = 1 AND (name LIKE ? OR description LIKE ?) ORDER BY visits DESC LIMIT 20";

    private static final String SELECT_BY_WARP_NAME =
            "SELECT * FROM swarp_warps WHERE name = ? AND public_warp = 1 LIMIT 1";

    private static final String DELETE_BY_ID =
            "DELETE FROM swarp_warps WHERE id = ?";

    private static final String UPDATE_VISITS =
            "UPDATE swarp_warps SET visits = visits + 1 WHERE id = ?";

    private static final String UPDATE_DESCRIPTION =
            "UPDATE swarp_warps SET description = ? WHERE id = ?";

    private static final String UPDATE_CATEGORY =
            "UPDATE swarp_warps SET category = ? WHERE id = ?";

    private static final String UPDATE_NAME =
            "UPDATE swarp_warps SET name = ? WHERE id = ?";

    private static final String UPDATE_EXPIRES =
            "UPDATE swarp_warps SET expires = ? WHERE id = ?";

    private static final String COUNT_BY_OWNER =
            "SELECT COUNT(*) FROM swarp_warps WHERE owner_uuid = ?";

    private static final String UPSERT_LAST_SEEN =
            "INSERT INTO swarp_player_activity (uuid, last_seen) VALUES (?, NOW()) " +
            "ON DUPLICATE KEY UPDATE last_seen = NOW()";

    // Only deletes warps where the player explicitly enabled expires = 1
    private static final String SELECT_EXPIRED_WARPS =
            "SELECT w.* FROM swarp_warps w " +
            "LEFT JOIN swarp_player_activity a ON w.owner_uuid = a.uuid " +
            "WHERE w.expires = 1 " +
            "AND (a.last_seen IS NULL OR a.last_seen < DATE_SUB(NOW(), INTERVAL ? DAY))";

    private final DatabaseManager db;

    @Inject
    public WarpRepository(DatabaseManager db) {
        this.db = db;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Write
    // ──────────────────────────────────────────────────────────────────────────

    @AsyncQuery("Insert a new warp")
    public int insert(PlayerWarp warp) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_WARP, Statement.RETURN_GENERATED_KEYS)) {
            Location loc = warp.location();
            ps.setString(1, warp.ownerUuid().toString());
            ps.setString(2, warp.ownerName());
            ps.setString(3, warp.name());
            ps.setString(4, loc.getWorld().getName());
            ps.setDouble(5, loc.getX());
            ps.setDouble(6, loc.getY());
            ps.setDouble(7, loc.getZ());
            ps.setFloat(8, loc.getYaw());
            ps.setFloat(9, loc.getPitch());
            ps.setString(10, warp.description());
            ps.setBoolean(11, warp.publicWarp());
            ps.setString(12, warp.category().name());
            ps.setBoolean(13, warp.expires());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    @AsyncQuery("Toggle expires flag for a warp")
    public void updateExpires(int warpId, boolean expires) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_EXPIRES)) {
            ps.setBoolean(1, expires);
            ps.setInt(2, warpId);
            ps.executeUpdate();
        }
    }

    @AsyncQuery("Increment visit counter")
    public void incrementVisits(int warpId) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_VISITS)) {
            ps.setInt(1, warpId);
            ps.executeUpdate();
        }
    }

    @AsyncQuery("Update description")
    public void updateDescription(int warpId, String description) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_DESCRIPTION)) {
            ps.setString(1, description);
            ps.setInt(2, warpId);
            ps.executeUpdate();
        }
    }

    @AsyncQuery("Update category")
    public void updateCategory(int warpId, WarpCategory category) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_CATEGORY)) {
            ps.setString(1, category.name());
            ps.setInt(2, warpId);
            ps.executeUpdate();
        }
    }

    @AsyncQuery("Rename a warp")
    public void updateName(int warpId, String newName) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_NAME)) {
            ps.setString(1, newName);
            ps.setInt(2, warpId);
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

    @AsyncQuery("Upsert last-seen for expire tracking")
    public void updateLastSeen(UUID uuid) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(UPSERT_LAST_SEEN)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────────────────────

    @AsyncQuery("Load all warps for a player")
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

    @AsyncQuery("Find warp by owner + name")
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

    @AsyncQuery("Load all public warps")
    public List<PlayerWarp> findAllPublic() throws SQLException {
        List<PlayerWarp> result = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL_PUBLIC);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        }
        return result;
    }

    @AsyncQuery("Filter by category")
    public List<PlayerWarp> findByCategory(WarpCategory category) throws SQLException {
        List<PlayerWarp> result = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_CATEGORY)) {
            ps.setString(1, category.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    @AsyncQuery("Search by keyword")
    public List<PlayerWarp> search(String keyword) throws SQLException {
        String pattern = "%" + keyword + "%";
        List<PlayerWarp> result = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_SEARCH)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    @AsyncQuery("Find public warp by name")
    public Optional<PlayerWarp> findPublicByName(String name) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_WARP_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @AsyncQuery("Count warps owned by player")
    public int countByOwner(UUID ownerUuid) throws SQLException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(COUNT_BY_OWNER)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @AsyncQuery("Find warps with expires=true from inactive players")
    public List<PlayerWarp> findExpiredWarps(int daysInactive) throws SQLException {
        List<PlayerWarp> result = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_EXPIRED_WARPS)) {
            ps.setInt(1, daysInactive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapping
    // ──────────────────────────────────────────────────────────────────────────

    private PlayerWarp mapRow(ResultSet rs) throws SQLException {
        World world = Bukkit.getWorld(rs.getString("world"));
        Location loc = new Location(world,
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"));

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
                .category(WarpCategory.fromString(rs.getString("category")))
                .expires(rs.getBoolean("expires"))
                .build();
    }
}
