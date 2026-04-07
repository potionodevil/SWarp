package de.swarp.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Singleton
public class SchemaInitializer {

    private final DatabaseManager db;

    @Inject
    public SchemaInitializer(DatabaseManager db) {
        this.db = db;
    }

    public void initialize() throws SQLException {
        try (Connection con = db.getConnection(); Statement stmt = con.createStatement()) {

            // Main warp table
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS swarp_warps (
                        id           INT           NOT NULL AUTO_INCREMENT,
                        owner_uuid   VARCHAR(36)   NOT NULL,
                        owner_name   VARCHAR(16)   NOT NULL,
                        name         VARCHAR(32)   NOT NULL,
                        world        VARCHAR(64)   NOT NULL,
                        x            DOUBLE        NOT NULL,
                        y            DOUBLE        NOT NULL,
                        z            DOUBLE        NOT NULL,
                        yaw          FLOAT         NOT NULL DEFAULT 0,
                        pitch        FLOAT         NOT NULL DEFAULT 0,
                        description  VARCHAR(128)  NOT NULL DEFAULT '',
                        category     VARCHAR(16)   NOT NULL DEFAULT 'OTHER',
                        public_warp  TINYINT(1)    NOT NULL DEFAULT 1,
                        visits       BIGINT        NOT NULL DEFAULT 0,
                        created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_owner_name (owner_uuid, name),
                        INDEX idx_public   (public_warp),
                        INDEX idx_owner    (owner_uuid),
                        INDEX idx_category (category)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            // Tracks last seen time for expire worker
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS swarp_player_activity (
                        uuid        VARCHAR(36)  NOT NULL,
                        last_seen   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                 ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);

            // Add category column to existing installations (safe to run multiple times)
            try {
                stmt.executeUpdate("""
                        ALTER TABLE swarp_warps
                        ADD COLUMN IF NOT EXISTS category VARCHAR(16) NOT NULL DEFAULT 'OTHER';
                        """);
            } catch (SQLException ignored) {
                // Already exists — MySQL < 8.0 doesn't support IF NOT EXISTS on ALTER
            }
        }
    }
}
