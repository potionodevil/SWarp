package de.swarp.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Root Guice module for SWarp.
 *
 * Binding philosophy:
 *  - Domain services and repositories → @Singleton (one instance per plugin lifecycle)
 *  - Workers → NOT bound here; the factory creates them via `new` (factory pattern)
 *  - JavaPlugin / PluginConfig → provided explicitly so Guice can inject them
 */
public class SwarpModule extends AbstractModule {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;

    public SwarpModule(JavaPlugin plugin, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
    }

    @Override
    protected void configure() {
        // All @Singleton-annotated classes are auto-discovered;
        // no explicit bind() needed when using @Inject constructors.
        // We only need to bridge the non-Guice-constructed instances below.
    }

    @Provides
    @Singleton
    JavaPlugin providePlugin() {
        return plugin;
    }

    @Provides
    @Singleton
    PluginConfig provideConfig() {
        return pluginConfig;
    }
}
