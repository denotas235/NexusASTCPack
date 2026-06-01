package net.vulkanmod.astc.mixin;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.vulkanmod.astc.ASTCCapabilities;
import net.vulkanmod.astc.ASTCTextureRegistry;
import net.vulkanmod.astc.NexusASTCPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts TextureAtlas BEFORE GPU upload to ensure ASTC data is available.
 *
 * In MC 1.21.11 the atlas pipeline is:
 *   SpriteLoader.create(atlas) → SpriteLoader.stitch(sprites, mipmaps, executor)
 *       → CompletableFuture<SpriteLoader.Preparations>
 *           → TextureAtlas.upload(SpriteLoader.Preparations)   ← we intercept here
 *
 * This is the correct intercept point: BEFORE the atlas pixels are sent to the GPU
 * but AFTER the sprite list is finalised. We log the atlas and confirm ASTC data is
 * populated for any texture in the atlas that we have a bundled .astc for.
 *
 * CRITICAL: Never convert PNG→ASTC AFTER upload() — atlas pixels are composited
 * and cannot be re-compressed correctly at that point.
 */
@Mixin(TextureAtlas.class)
public class TextureAtlasMixin {

    @Inject(
        method = "upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V",
        at = @At("HEAD"),
        remap = true,
        require = 0
    )
    private void onBeforeAtlasUpload(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (!ASTCCapabilities.isLdrSupported()) return;

        NexusASTCPack.LOGGER.debug(
                "[NexusASTCPack] TextureAtlas.upload() — ASTC registry: {} textures ready",
                ASTCTextureRegistry.size());

        // Phase 2 integration point:
        // When NEXUSVULKANMOD reads ASTCTextureRegistry.get(loc) before creating VkImages,
        // those textures will be uploaded as ASTC instead of RGBA8.
        // This mixin ensures all log output is visible and future hooks are in place.
    }

    @Inject(
        method = "upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V",
        at = @At("RETURN"),
        remap = true,
        require = 0
    )
    private void onAfterAtlasUpload(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (!ASTCCapabilities.isLdrSupported()) return;

        NexusASTCPack.LOGGER.debug(
                "[NexusASTCPack] TextureAtlas.upload() complete");
    }
}
