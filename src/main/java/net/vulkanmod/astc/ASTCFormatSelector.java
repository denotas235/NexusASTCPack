package net.vulkanmod.astc;

import net.minecraft.resources.ResourceLocation;

/**
 * Selects the Vulkan ASTC format and block size for a given texture resource location.
 *
 * VkFormat constants:
 *   ASTC 4×4 UNORM  = 157  SRGB = 158
 *   ASTC 6×6 UNORM  = 165  SRGB = 166
 *   ASTC 8×8 UNORM  = 171  SRGB = 172
 *   ASTC 4×4 SFLOAT = 1000066000  (VK_EXT_texture_compression_astc_hdr)
 */
public class ASTCFormatSelector {

    public static final int ASTC_4x4_UNORM  = 157;
    public static final int ASTC_4x4_SRGB   = 158;
    public static final int ASTC_6x6_UNORM  = 165;
    public static final int ASTC_6x6_SRGB   = 166;
    public static final int ASTC_8x8_UNORM  = 171;
    public static final int ASTC_8x8_SRGB   = 172;
    public static final int ASTC_4x4_SFLOAT = 1000066000; // VK_EXT_texture_compression_astc_hdr

    public record FormatInfo(int vkFormat, int blockX, int blockY, boolean srgb) {
        public String blockSizeStr() { return blockX + "x" + blockY; }
    }

    public static FormatInfo select(ResourceLocation location) {
        String path = location.getPath(); // e.g. "textures/block/stone"

        // Normal maps — linear, fine detail → 4×4 UNORM
        if (path.endsWith("_n") || path.contains("normal") || path.endsWith("_normal")) {
            return new FormatInfo(ASTC_4x4_UNORM, 4, 4, false);
        }

        // Environment / sky / sun / moon — HDR preferred
        if (path.startsWith("textures/environment/") || path.contains("/sun") || path.contains("/moon")) {
            if (ASTCCapabilities.isHdrSupported()) {
                return new FormatInfo(ASTC_4x4_SFLOAT, 4, 4, false);
            }
            return new FormatInfo(ASTC_4x4_UNORM, 4, 4, false);
        }

        // GUI elements, font bitmaps, icons — 4×4 sRGB for sharpness
        if (path.startsWith("textures/gui/") || path.startsWith("textures/font/")
                || path.startsWith("textures/misc/")) {
            return new FormatInfo(ASTC_4x4_SRGB, 4, 4, true);
        }

        // Items — 4×4 sRGB to preserve fine pixel-art details
        if (path.startsWith("textures/item/") || path.startsWith("textures/items/")) {
            return new FormatInfo(ASTC_4x4_SRGB, 4, 4, true);
        }

        // Particles / effects — less critical, 6×6 saves bandwidth
        if (path.startsWith("textures/particle/") || path.startsWith("textures/effect/")) {
            return new FormatInfo(ASTC_6x6_UNORM, 6, 6, false);
        }

        // Transparent blocks (glass, leaves, ice) — 4×4 to preserve alpha fidelity
        if (path.contains("glass") || path.contains("leaves") || path.contains("ice")) {
            return new FormatInfo(ASTC_4x4_SRGB, 4, 4, true);
        }

        // Entities, mobs — 6×6 sRGB
        if (path.startsWith("textures/entity/")) {
            return new FormatInfo(ASTC_6x6_SRGB, 6, 6, true);
        }

        // Distant terrain / lod — 8×8 for maximum bandwidth savings
        if (path.contains("terrain") || path.contains("distant") || path.contains("lod")) {
            return new FormatInfo(ASTC_8x8_SRGB, 8, 8, true);
        }

        // Default: block textures 6×6 sRGB
        return new FormatInfo(ASTC_6x6_SRGB, 6, 6, true);
    }
}
