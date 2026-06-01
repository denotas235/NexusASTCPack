package net.vulkanmod.astc.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.vulkanmod.astc.ASTCCapabilities;
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
 * Phase 1: logs ASTC availability (no-op — still uses RGBA8 upload).
 * Phase 2 (NEXUSVULKANMOD integration): cancel upload and send ASTC data to GPU.
 *
 * require = 0: if GlCommandEncoder changes signature, the mixin is silently skipped.
 */
@Mixin(value = GlCommandEncoder.class, priority = 1500, remap = true)
public abstract class SimpleTextureUploadMixin {

    @Inject(
        method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;)V",
        at = @At("HEAD"),
        require = 0,
        cancellable = false
    )
    private void onWriteToTexture(GpuTexture gpuTexture, NativeImage nativeImage, CallbackInfo ci) {
        // Phase 1: capability guard only. Texture identification via GPU label is deferred
        // to Phase 2 (NEXUSVULKANMOD), which has atlas metadata available.
        if (!ASTCCapabilities.isLdrSupported()) return;
        // TODO Phase 2: cancel default upload, serve ASTC bytes via VulkanMod API
    }
}
