package net.vulkanmod.astc.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.astc.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts GlCommandEncoder.writeToTexture(GpuTexture, NativeImage) — the OpenGL
 * backend's texture upload path.
 *
 * NOTE: This fires only when the OpenGL backend is active (i.e. WITHOUT VulkanMod).
 * When VulkanMod is installed it replaces GlCommandEncoder entirely with its own
 * Vulkan-based CommandEncoder implementation.
 *
 * VulkanMod integration (Phase 2) is done by updating NEXUSVULKANMOD's existing
 * TextureUploadMixin stubs to call ASTCTextureRegistry.get(location) before uploading.
 * This class serves as the Phase 1 documentation and OpenGL compatibility path.
 *
 * When ASTC data is available:
 *   Phase 1: logs that ASTC data exists (no-op upload, RGBA8 still used)
 *   Phase 2: cancels default upload, calls Vulkan ASTC upload via NEXUSVULKANMOD
 */
@Mixin(value = GlCommandEncoder.class, priority = 1500, remap = true)
public class SimpleTextureUploadMixin {

    @Inject(
        method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;)V",
        at = @At("HEAD"),
        require = 0,
        cancellable = false   // Phase 1: no cancel; Phase 2: set true in NEXUSVULKANMOD
    )
    private void onWriteToTexture(GpuTexture gpuTexture, NativeImage nativeImage, CallbackInfo ci) {
        if (!ASTCCapabilities.isLdrSupported()) return;

        String label = gpuTexture.debugLabel();
        if (label == null || label.isEmpty()) return;

        ResourceLocation loc = tryParseLabel(label);
        if (loc == null) return;

        ASTCTextureRegistry.ASTCEntry entry = ASTCTextureRegistry.get(loc);
        if (entry != null) {
            NexusASTCPack.LOGGER.debug(
                "[NexusASTCPack] ASTC available for {} — vkFmt={} {}x{} — " +
                "Phase 2 needed to use it with VulkanMod",
                loc, entry.vkFormat(), entry.blockX(), entry.blockY());
            return;
        }

        // Not in registry — schedule async encode if astcenc is available
        if (ASTCEncoder.isAvailable() && nativeImage != null) {
            ASTCFormatSelector.FormatInfo fmt = ASTCFormatSelector.select(loc);
            // Check disk cache first (avoids re-encoding)
            byte[] cached = ASTCTextureCache.lookup(loc, fmt.blockX(), fmt.blockY());
            if (cached != null) {
                ASTCTextureRegistry.put(loc,
                    new ASTCTextureRegistry.ASTCEntry(cached, fmt.vkFormat(), fmt.blockX(), fmt.blockY()));
                NexusASTCPack.LOGGER.debug("[NexusASTCPack] Loaded from cache: {}", loc);
            }
            // TODO Phase 2: export NativeImage pixels as PNG bytes and trigger async encode
        }
    }

    private static ResourceLocation tryParseLabel(String label) {
        try {
            if (label.contains(":")) {
                String[] p = label.split(":", 2);
                String path = p[1].endsWith(".png") ? p[1].substring(0, p[1].length() - 4) : p[1];
                return ResourceLocation.fromNamespaceAndPath(p[0], path);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
