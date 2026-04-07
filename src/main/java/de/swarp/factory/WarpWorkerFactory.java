package de.swarp.factory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.database.repository.WarpRepository;
import de.swarp.effects.WarpEffectService;
import de.swarp.guice.PluginConfig;
import de.swarp.service.WarpCacheService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Factory responsible for instantiating {@link WarpWorker}s and submitting
 * them to the thread pool.
 *
 * Uses a virtual-thread executor for DB workers and a scheduled executor
 * for countdown timers — both separate from Bukkit's scheduler to avoid
 * blocking the main thread.
 */
@Singleton
public class WarpWorkerFactory {

    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler;

    private final JavaPlugin plugin;
    private final WarpRepository repository;
    private final WarpCacheService cacheService;
    private final WarpEffectService effectService;
    private final PluginConfig config;

    @Inject
    public WarpWorkerFactory(JavaPlugin plugin,
                             WarpRepository repository,
                             WarpCacheService cacheService,
                             WarpEffectService effectService,
                             PluginConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.cacheService = cacheService;
        this.effectService = effectService;
        this.config = config;
        this.workerPool = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduler = Executors.newScheduledThreadPool(
                2,
                r -> Thread.ofVirtual().name("swarp-scheduler").unstarted(r)
        );
    }

    /**
     * Creates a worker for the given task and submits it to the thread pool.
     * Returns immediately — the work happens asynchronously.
     */
    public void submit(WarpTask task) {
        WarpWorker worker = new WarpWorker(
                task, plugin, repository, cacheService, effectService, config, scheduler);
        workerPool.submit(worker);
    }

    /**
     * Gracefully shuts down both executors. Called on plugin disable.
     */
    public void shutdown() {
        try {
            workerPool.shutdown();
            scheduler.shutdown();
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "Interrupted during executor shutdown", e);
            Thread.currentThread().interrupt();
        }
    }
}
