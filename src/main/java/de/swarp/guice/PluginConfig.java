package de.swarp.guice;

import com.google.inject.Singleton;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Thin, injectable wrapper around Bukkit's {@link FileConfiguration}.
 * Centralises all config access so consumers never hold a direct reference
 * to the raw config object.
 */
@Singleton
public class PluginConfig {

    private FileConfiguration config;

    public void reload(FileConfiguration config) {
        this.config = config;
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public long getLong(String path, long def) {
        return config.getLong(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }
}
