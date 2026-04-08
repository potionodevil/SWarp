package de.swarp.factory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.database.repository.WarpRepository;
import de.swarp.effects.WarpEffectService;
import de.swarp.guice.PluginConfig;
import de.swarp.service.WarpCacheService;
import de.swarp.service.WarpPermissionService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Singleton
public class WarpWorkerFactory {

    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler;

    private final JavaPlugin plugin;
    private final WarpRepository repository;
    private final WarpCacheService cacheService;
    private final WarpEffectService effectService;
    private final PluginConfig config;
    private final WarpPermissionService permissionService;

    @Inject
    public WarpWorkerFactory(JavaPlugin plugin,
                             WarpRepository repository,
                             WarpCacheService cacheService,
                             WarpEffectService effectService,
                             PluginConfig config,
                             WarpPermissionService permissionService) {
        this.plugin = plugin;
        this.repository = repository;
        this.cacheService = cacheService;
        this.effectService = effectService;
        this.config = config;
        this.permissionService = permissionService;

        this.workerPool = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduler  = Executors.newScheduledThreadPool(
                2, r -> Thread.ofVirtual().name("swarp-scheduler").unstarted(r));
    }

    public void submit(WarpTask task) {
        WarpWorker worker = new WarpWorker(
                task, plugin, repository, cacheService,
                effectService, config, scheduler, permissionService);
        workerPool.submit(worker);
    }

    public void shutdown() {
        try {
            workerPool.shutdown();
            scheduler.shutdown();
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) workerPool.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS))  scheduler.shutdownNow();
        } catch (InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "Interrupted during executor shutdown", e);
            Thread.currentThread().interrupt();
        }
    }
}
