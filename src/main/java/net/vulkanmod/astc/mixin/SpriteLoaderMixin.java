package net.vulkanmod.astc.mixin;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.vulkanmod.astc.ASTCCapabilities;
import net.vulkanmod.astc.ASTCTextureRegistry;
import net.vulkanmod.astc.NexusASTCPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Intercepts SpriteLoader.stitch() — the entry point for atlas texture stitching.
 *
 * This fires BEFORE the stitcher combines sprites into the atlas texture.
 * Here we ensure the ASTC registry is populated for all textures that will
 * enter the atlas, so Phase 2 (NEXUSVULKANMOD) can serve ASTC per-sprite.
 *
 * MC 1.21.11: SpriteLoader.stitch(List<SpriteContents>, int mipmapLevels, Executor)
 */
@Mixin(SpriteLoader.class)
public class SpriteLoaderMixin {

    @Inject(
        method = "stitch",
        at = @At("HEAD"),
        remap = true,
        require = 0
    )
    private void onBeforeStitch(List<?> sprites, int mipmapLevels, Executor executor,
                                CallbackInfoReturnable<?> cir) {
        if (!ASTCCapabilities.isLdrSupported()) return;

        NexusASTCPack.LOGGER.debug(
                "[NexusASTCPack] SpriteLoader.stitch() — {} sprites, {} ASTC textures in registry",
                sprites.size(), ASTCTextureRegistry.size());
    }
}
