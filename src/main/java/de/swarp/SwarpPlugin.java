package de.swarp;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.swarp.command.CommandDispatcher;
import de.swarp.command.SwarpCommand;
import de.swarp.command.SwarpCommandHandler;
import de.swarp.database.DatabaseManager;
import de.swarp.database.SchemaInitializer;
import de.swarp.database.repository.WarpRepository;
import de.swarp.factory.WarpExpireWorker;
import de.swarp.factory.WarpWorkerFactory;
import de.swarp.guice.PluginConfig;
import de.swarp.guice.SwarpModule;
import de.swarp.listener.PlayerJoinListener;
import de.swarp.listener.WarpSignListener;
import de.swarp.service.SwarpPlaceholderExpansion;
import de.swarp.service.WarpCacheService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * @author Rene for NitroMC
 * @version 1.0.0
 */

public final class SwarpPlugin extends JavaPlugin {

    private Injector injector;
    private DatabaseManager databaseManager;
    private WarpWorkerFactory workerFactory;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.reload(getConfig());

        injector = Guice.createInjector(new SwarpModule(this, pluginConfig));

        databaseManager = injector.getInstance(DatabaseManager.class);
        try {
            injector.getInstance(SchemaInitializer.class).initialize();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "DB-Initialisierung fehlgeschlagen — deaktiviere Plugin!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Warm cache
        WarpRepository repository = injector.getInstance(WarpRepository.class);
        WarpCacheService cache    = injector.getInstance(WarpCacheService.class);
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                cache.putAll(repository.findAllPublic());
                getLogger().info("[SWarp] " + cache.getAllPublic().size() + " Warps geladen.");
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Cache-Vorladung fehlgeschlagen", e);
            }
        });
        workerFactory = injector.getInstance(WarpWorkerFactory.class);
        CommandDispatcher dispatcher = new CommandDispatcher(getLogger());
        dispatcher.register(injector.getInstance(SwarpCommandHandler.class));

        PluginCommand cmd = getCommand("swarp");
        if (cmd != null) {
            SwarpCommand executor = new SwarpCommand(dispatcher);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(injector.getInstance(PlayerJoinListener.class), this);
        getServer().getPluginManager().registerEvents(injector.getInstance(WarpSignListener.class), this);
        injector.getInstance(WarpExpireWorker.class).schedule();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            injector.getInstance(SwarpPlaceholderExpansion.class).register();
            getLogger().info("[SWarp] PlaceholderAPI-Integration aktiviert.");
        } else {
            getLogger().info("[SWarp] PlaceholderAPI nicht gefunden — Placeholders deaktiviert.");
        }

        getLogger().info("[SWarp] Plugin aktiviert. ✦");
    }

    @Override
    public void onDisable() {
        if (workerFactory != null) workerFactory.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
        getLogger().info("[SWarp] Plugin deaktiviert.");
    }
}
