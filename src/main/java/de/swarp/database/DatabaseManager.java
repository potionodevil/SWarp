package de.swarp.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.swarp.guice.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Manages the HikariCP connection pool.
 * Lifecycle: opened on plugin enable, closed on disable.
 */
@Singleton
public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final Logger logger;

    @Inject
    public DatabaseManager(JavaPlugin plugin, PluginConfig config) {
        this.logger = plugin.getLogger();

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true&characterEncoding=UTF-8",
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.database", "swarp")
        ));
        hikari.setUsername(config.getString("database.username", "root"));
        hikari.setPassword(config.getString("database.password", "password"));
        hikari.setMaximumPoolSize(config.getInt("database.pool-size", 10));
        hikari.setConnectionTimeout(config.getLong("database.connection-timeout", 30000L));
        hikari.setPoolName("SWarp-Pool");

        // Performance tuning
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");

        this.dataSource = new HikariDataSource(hikari);
        logger.info("[SWarp] Database pool initialized.");
    }

    /** Borrow a connection from the pool. Must be closed after use (try-with-resources). */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Gracefully shuts down the connection pool. */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[SWarp] Database pool closed.");
        }
    }
}
