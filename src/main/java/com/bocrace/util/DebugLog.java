package com.bocrace.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Debug logging utility with file rotation and structured tags
 */
public class DebugLog {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    public enum Tag {
        DATA, STATE, DETECT, RULE, CMD, PERM, PERF, ERROR
    }
    
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }
    
    private final Plugin plugin;
    private final ReentrantLock lock = new ReentrantLock();
    private BufferedWriter writer;
    private boolean enabled;
    private long maxFileSizeBytes;
    private int maxFiles;
    private File logFile;
    
    public DebugLog(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
        initializeLogFile();
    }
    
    private void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("debug.enabled", false);
        int maxFileSizeMB = config.getInt("debug.maxFileSizeMB", 5);
        maxFiles = config.getInt("debug.maxFiles", 5);
        maxFileSizeBytes = maxFileSizeMB * 1024L * 1024L;
    }
    
    private void initializeLogFile() {
        if (!enabled) {
            return;
        }
        
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        logFile = new File(dataFolder, "debug.log");
        
        try {
            writer = new BufferedWriter(new FileWriter(logFile, true));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to initialize debug.log: " + e.getMessage());
            enabled = false;
        }
    }
    
    /**
     * Check if rotation is needed and perform it
     */
    private void checkRotation() {
        if (!enabled || logFile == null || !logFile.exists()) {
            return;
        }
        
        if (logFile.length() > maxFileSizeBytes) {
            lock.lock();
            try {
                // Close current writer
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
                
                // Rotate files: debug.N.log -> debug.(N+1).log
                for (int i = maxFiles - 1; i >= 1; i--) {
                    File oldFile = new File(plugin.getDataFolder(), "debug." + i + ".log");
                    File newFile = new File(plugin.getDataFolder(), "debug." + (i + 1) + ".log");
                    if (oldFile.exists()) {
                        if (i + 1 > maxFiles) {
                            // Delete oldest file
                            oldFile.delete();
                        } else {
                            oldFile.renameTo(newFile);
                        }
                    }
                }
                
                // debug.log -> debug.1.log
                File oldDebug = new File(plugin.getDataFolder(), "debug.log");
                if (oldDebug.exists()) {
                    File newFile = new File(plugin.getDataFolder(), "debug.1.log");
                    oldDebug.renameTo(newFile);
                }
                
                // Create new debug.log
                logFile = new File(plugin.getDataFolder(), "debug.log");
                writer = new BufferedWriter(new FileWriter(logFile, true));
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to rotate debug.log: " + e.getMessage());
                enabled = false;
            } finally {
                lock.unlock();
            }
        }
    }
    
    /**
     * Write a log line
     */
    private void writeLine(String line) {
        if (!enabled || writer == null) {
            return;
        }
        
        lock.lock();
        try {
            checkRotation();
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            // Fail silently after first warning
            plugin.getLogger().warning("Failed to write to debug.log: " + e.getMessage());
            enabled = false;
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e2) {
                // Ignore
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Format a key-value map for logging
     */
    private String formatKeyValues(Map<String, Object> kv) {
        if (kv == null || kv.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : kv.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            Object value = entry.getValue();
            String valueStr = value != null ? value.toString() : "null";
            sb.append(entry.getKey()).append("=").append(valueStr);
        }
        return sb.toString();
    }
    
    /**
     * Escape and quote a message string
     */
    private String formatMessage(String msg) {
        if (msg == null) {
            return "null";
        }
        // Escape quotes
        String escaped = msg.replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
    
    /**
     * Build log line
     */
    private String buildLogLine(Tag tag, Level level, String category, String msg, Map<String, Object> kv) {
        String timestamp = DATE_FORMAT.format(new Date());
        String kvStr = formatKeyValues(kv);
        String msgStr = formatMessage(msg);
        
        StringBuilder sb = new StringBuilder(timestamp);
        sb.append(" [BOCRacingV2] [").append(level.name()).append("] [").append(tag.name()).append("] [").append(category).append("]");
        if (!kvStr.isEmpty()) {
            sb.append(" ").append(kvStr);
        }
        sb.append(" msg=").append(msgStr);
        return sb.toString();
    }
    
    public void debug(Tag tag, String category, String msg, Map<String, Object> kv) {
        if (!enabled) return;
        writeLine(buildLogLine(tag, Level.DEBUG, category, msg, kv));
    }
    
    public void debug(Tag tag, String category, String msg) {
        debug(tag, category, msg, null);
    }
    
    public void info(Tag tag, String category, String msg, Map<String, Object> kv) {
        if (!enabled) return;
        writeLine(buildLogLine(tag, Level.INFO, category, msg, kv));
    }
    
    public void info(Tag tag, String category, String msg) {
        info(tag, category, msg, null);
    }
    
    public void warn(Tag tag, String category, String msg, Map<String, Object> kv) {
        if (!enabled) return;
        writeLine(buildLogLine(tag, Level.WARN, category, msg, kv));
    }
    
    public void warn(Tag tag, String category, String msg) {
        warn(tag, category, msg, null);
    }
    
    /**
     * Error always logs to console + debug file if enabled
     */
    public void error(String category, String msg, Throwable t, Map<String, Object> kv) {
        // Always log to console
        if (t != null) {
            plugin.getLogger().severe("[" + category + "] " + msg);
            t.printStackTrace();
        } else {
            plugin.getLogger().severe("[" + category + "] " + msg);
        }
        
        // Also write to debug file if enabled
        if (enabled) {
            String errorMsg = msg;
            if (t != null) {
                errorMsg += " | Exception: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            writeLine(buildLogLine(Tag.ERROR, Level.ERROR, category, errorMsg, kv));
        }
    }
    
    public void error(String category, String msg, Throwable t) {
        error(category, msg, t, null);
    }
    
    public void error(String category, String msg) {
        error(category, msg, null, null);
    }
    
    /**
     * Close writer cleanly
     */
    public void close() {
        lock.lock();
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Error closing debug.log: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}
