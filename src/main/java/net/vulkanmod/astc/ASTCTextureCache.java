package net.vulkanmod.astc;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe disk cache for runtime-converted ASTC textures.
 *
 * Location : .minecraft/astc-cache/
 * Key      : <namespace>_<path>_<blockX>x<blockY>.astc
 * Max age  : 30 days (auto-pruned at startup)
 * Max size : 512 MB (warning only, not enforced by deletion)
 */
public class ASTCTextureCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("NexusASTCPack");
    static final long MAX_SIZE_BYTES = 512L * 1024 * 1024;
    static final int  MAX_AGE_DAYS  = 30;

    private static final ConcurrentHashMap<String, byte[]> MEMORY = new ConcurrentHashMap<>(256);
    private static volatile Path cacheDir;

    public static void init() {
        cacheDir = FabricLoader.getInstance().getGameDir().resolve("astc-cache");
        try {
            Files.createDirectories(cacheDir);
            LOGGER.info("[NexusASTCPack] Cache dir: {}", cacheDir);
            ASTCTextureRegistry.getBackgroundExecutor().execute(ASTCTextureCache::prune);
        } catch (IOException e) {
            LOGGER.error("[NexusASTCPack] Cannot create cache dir: {}", e.getMessage());
        }
    }

    private static String key(ResourceLocation loc, int blockX, int blockY) {
        return loc.getNamespace() + "_"
             + loc.getPath().replace('/', '_')
             + "_" + blockX + "x" + blockY + ".astc";
    }

    /**
     * Returns cached ASTC bytes or null if not in cache.
     */
    public static byte[] lookup(ResourceLocation loc, int blockX, int blockY) {
        String k = key(loc, blockX, blockY);
        byte[] mem = MEMORY.get(k);
        if (mem != null) return mem;
        if (cacheDir == null) return null;
        Path f = cacheDir.resolve(k);
        if (!Files.exists(f)) return null;
        try {
            byte[] data = Files.readAllBytes(f);
            MEMORY.put(k, data);
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Stores ASTC bytes in memory cache and disk.
     */
    public static void store(ResourceLocation loc, int blockX, int blockY, byte[] data) {
        String k = key(loc, blockX, blockY);
        MEMORY.put(k, data);
        if (cacheDir == null) return;
        try {
            Files.write(cacheDir.resolve(k), data,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("[NexusASTCPack] Cache write failed {}: {}", k, e.getMessage());
        }
    }

    private static void prune() {
        if (cacheDir == null) return;
        try {
            Instant cutoff = Instant.now().minus(MAX_AGE_DAYS, ChronoUnit.DAYS);
            long total = 0;
            int  deleted = 0;
            try (var stream = Files.list(cacheDir)) {
                for (Path p : stream.filter(f -> f.toString().endsWith(".astc")).toList()) {
                    BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                    if (attr.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        Files.delete(p);
                        deleted++;
                    } else {
                        total += attr.size();
                    }
                }
            }
            if (deleted > 0) LOGGER.info("[NexusASTCPack] Pruned {} stale cache files", deleted);
            if (total > MAX_SIZE_BYTES)
                LOGGER.warn("[NexusASTCPack] Cache is {:.1f} MB — consider clearing astc-cache/",
                        total / 1_048_576.0);
        } catch (IOException e) {
            LOGGER.debug("[NexusASTCPack] Prune error: {}", e.getMessage());
        }
    }
}
