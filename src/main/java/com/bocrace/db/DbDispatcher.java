package com.bocrace.db;

import com.bocrace.BOCRacingV2;
import com.bocrace.util.DebugLog;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-threaded database dispatcher for ordered execution
 * Ensures all database operations execute in order on a dedicated thread
 */
public class DbDispatcher {
    
    private final BOCRacingV2 plugin;
    private final ExecutorService executor;
    private final AtomicBoolean shutdown;
    
    public DbDispatcher(BOCRacingV2 plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BOCRacingV2-DB");
            t.setDaemon(true);
            return t;
        });
        this.shutdown = new AtomicBoolean(false);
    }
    
    /**
     * Submit a database operation (runs on DB thread, returns immediately)
     */
    public void submit(Runnable task) {
        if (shutdown.get()) {
            plugin.getDebugLog().warn(DebugLog.Tag.ERROR, "DbDispatcher", "Rejected task after shutdown", null);
            return;
        }
        
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getDebugLog().error("DbDispatcher", "Database task failed", e);
            }
        });
    }
    
    /**
     * Submit a database operation with return value (runs on DB thread, returns Future)
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (shutdown.get()) {
            plugin.getDebugLog().warn(DebugLog.Tag.ERROR, "DbDispatcher", "Rejected task after shutdown", null);
            return CompletableFuture.completedFuture(null);
        }
        
        return executor.submit(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                plugin.getDebugLog().error("DbDispatcher", "Database task failed", e);
                throw e;
            }
        });
    }
    
    /**
     * Shutdown dispatcher (waits for pending tasks, then terminates)
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    plugin.getLogger().warning("Database dispatcher did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            plugin.getDebugLog().info(DebugLog.Tag.DATA, "DbDispatcher", "Dispatcher shutdown", null);
        }
    }
    
    /**
     * Check if dispatcher is shutdown
     */
    public boolean isShutdown() {
        return shutdown.get();
    }
}
