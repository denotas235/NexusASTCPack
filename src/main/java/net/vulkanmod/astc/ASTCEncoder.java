package net.vulkanmod.astc;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/**
 * Wraps the ARM astcenc CLI encoder for CPU-side ASTC encoding.
 *
 * Pre-compilation (CI): uses astcenc-avx2 (Linux x86_64) with -thorough quality.
 * Runtime (third-party mod textures): uses the binary found at ASTCENC_PATH with -fast quality.
 *
 * JNI path (libastcenc-arm64.so) for on-device encoding is planned for Phase 2.
 */
public class ASTCEncoder {

    private static final Logger LOGGER = LoggerFactory.getLogger("NexusASTCPack");
    private static volatile String binary = null;

    static {
        String[] candidates = {
            System.getenv("ASTCENC_PATH"),
            "/usr/local/bin/astcenc-avx2",
            "/usr/local/bin/astcenc-neon",
            "/usr/local/bin/astcenc",
            "/usr/bin/astcenc-avx2",
            "/usr/bin/astcenc",
        };
        for (String c : candidates) {
            if (c != null && Files.isExecutable(Path.of(c))) {
                binary = c;
                LOGGER.info("[NexusASTCPack] astcenc found: {}", c);
                break;
            }
        }
        if (binary == null) {
            LOGGER.debug("[NexusASTCPack] astcenc not found — runtime encoding unavailable");
        }
    }

    public static boolean isAvailable() { return binary != null; }

    /**
     * Encodes PNG bytes to raw ASTC bytes using astcenc -fast.
     * Blocking — always call from the background executor.
     *
     * @param pngBytes  raw PNG file bytes
     * @param blockX    ASTC block width  (4, 6, or 8)
     * @param blockY    ASTC block height (4, 6, or 8)
     * @return raw ASTC file bytes (starts with 13-byte magic header)
     */
    public static byte[] encode(byte[] pngBytes, int blockX, int blockY) throws IOException {
        if (!isAvailable()) throw new IOException("astcenc binary not available");

        Path tmpIn  = Files.createTempFile("nexusastc_in_",  ".png");
        Path tmpOut = Files.createTempFile("nexusastc_out_", ".astc");
        try {
            Files.write(tmpIn, pngBytes);
            ProcessBuilder pb = new ProcessBuilder(
                binary, "-cl",
                tmpIn.toString(), tmpOut.toString(),
                blockX + "x" + blockY,
                "-fast"          // runtime: fast quality
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String log = new String(proc.getInputStream().readAllBytes());
            int exit;
            try { exit = proc.waitFor(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }
            if (exit != 0) throw new IOException("astcenc exit " + exit + ": " + log.lines().findFirst().orElse(""));
            return Files.readAllBytes(tmpOut);
        } finally {
            Files.deleteIfExists(tmpIn);
            Files.deleteIfExists(tmpOut);
        }
    }

    /**
     * Async encode + store in cache. Returns null on failure (non-fatal).
     */
    public static CompletableFuture<byte[]> encodeAsync(
            ResourceLocation location, byte[] pngBytes,
            int blockX, int blockY, int vkFormat) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] astcData = encode(pngBytes, blockX, blockY);
                ASTCTextureCache.store(location, blockX, blockY, astcData);
                ASTCTextureRegistry.put(location,
                        new ASTCTextureRegistry.ASTCEntry(astcData, vkFormat, blockX, blockY));
                LOGGER.debug("[NexusASTCPack] Async encoded & cached: {}", location);
                return astcData;
            } catch (Exception e) {
                LOGGER.debug("[NexusASTCPack] Encoding failed for {}: {}", location, e.getMessage());
                return null;
            }
        }, ASTCTextureRegistry.getBackgroundExecutor());
    }
}
