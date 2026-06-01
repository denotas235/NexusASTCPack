package net.vulkanmod.astc;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PUBLIC API — central registry of ASTC texture data.
 *
 * Other mods (including NEXUSVULKANMOD) query this registry to check whether
 * ASTC data is available for a texture before uploading it to the GPU.
 *
 * Usage from NEXUSVULKANMOD (optional dependency pattern):
 * <pre>
 *   if (FabricLoader.getInstance().isModLoaded("nexusastcpack")) {
 *       ASTCTextureRegistry.ASTCEntry entry = ASTCTextureRegistry.get(resourceLocation);
 *       if (entry != null) {
 *           // upload entry.data() as VkFormat entry.vkFormat() via Vulkan
 *           return;
 *       }
 *   }
 *   // fallback: normal RGBA8 upload
 * </pre>
 *
 * This registry is populated from:
 *   1. Bundled JAR resources (pre-converted vanilla textures) — via {@link ASTCResourceProvider}
 *   2. Disk cache for previously encoded third-party mod textures — via {@link ASTCTextureCache}
 *   3. Async encoding results — via {@link ASTCEncoder#encodeAsync}
 */
public class ASTCTextureRegistry {

    /**
     * Immutable ASTC texture entry.
     *
     * @param data     Raw ASTC file bytes (13-byte ARM magic header + compressed blocks).
     *                 Ready to be uploaded via vkCmdCopyBufferToImage with the given vkFormat.
     * @param vkFormat VkFormat constant — e.g. 165 = VK_FORMAT_ASTC_6x6_UNORM_BLOCK
     * @param blockX   ASTC block width  (4, 6, or 8)
     * @param blockY   ASTC block height (4, 6, or 8)
     */
    public record ASTCEntry(byte[] data, int vkFormat, int blockX, int blockY) {}

    private static final ConcurrentHashMap<String, ASTCEntry> REGISTRY = new ConcurrentHashMap<>(512);

    /** Single daemon background thread for async encode / cache I/O. */
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NexusASTCPack-BG");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    // ── Public read API ────────────────────────────────────────────────────

    /**
     * Returns the ASTC entry for a texture, or {@code null} if:
     * — ASTC hardware is not supported, OR
     * — no entry has been registered for this location.
     */
    public static @Nullable ASTCEntry get(ResourceLocation location) {
        if (!ASTCCapabilities.isLdrSupported()) return null;
        return REGISTRY.get(location.toString());
    }

    /** Returns true if ASTC is supported and an entry exists for this location. */
    public static boolean has(ResourceLocation location) {
        return ASTCCapabilities.isLdrSupported() && REGISTRY.containsKey(location.toString());
    }

    /** Total number of registered ASTC textures. */
    public static int size() { return REGISTRY.size(); }

    // ── Mutation API (called from ASTCResourceProvider / ASTCEncoder) ──────

    public static void put(ResourceLocation location, ASTCEntry entry) {
        REGISTRY.put(location.toString(), entry);
    }

    public static void remove(ResourceLocation location) {
        REGISTRY.remove(location.toString());
    }

    /** Called at the start of each resource reload to clear stale entries. */
    public static void clear() {
        REGISTRY.clear();
    }

    /** Background executor. Use for async encoding and cache I/O only. */
    public static ExecutorService getBackgroundExecutor() { return BACKGROUND; }
}
