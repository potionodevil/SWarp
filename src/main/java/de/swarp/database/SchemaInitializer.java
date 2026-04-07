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
                        expires      TINYINT(1)    NOT NULL DEFAULT 0,
                        visits       BIGINT        NOT NULL DEFAULT 0,
                        created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_owner_name (owner_uuid, name),
                        INDEX idx_public   (public_warp),
                        INDEX idx_owner    (owner_uuid),
                        INDEX idx_category (category),
                        INDEX idx_expires  (expires)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS swarp_player_activity (
                        uuid        VARCHAR(36)  NOT NULL,
                        last_seen   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                 ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);

            // Safe migrations for existing installations
            for (String migration : new String[]{
                "ALTER TABLE swarp_warps ADD COLUMN IF NOT EXISTS category VARCHAR(16) NOT NULL DEFAULT 'OTHER'",
                "ALTER TABLE swarp_warps ADD COLUMN IF NOT EXISTS expires TINYINT(1) NOT NULL DEFAULT 0"
            }) {
                try { stmt.executeUpdate(migration); }
                catch (SQLException ignored) { /* already exists on older MySQL */ }
            }
        }
    }
}
