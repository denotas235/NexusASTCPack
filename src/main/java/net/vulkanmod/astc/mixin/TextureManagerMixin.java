package net.vulkanmod.astc.mixin;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.vulkanmod.astc.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts TextureManager.register(Identifier, AbstractTexture) to
 * warm-start the disk cache for textures loaded by third-party mods.
 *
 * When a texture is registered that is NOT already in the ASTC registry
 * and a disk-cached version exists, it is promoted to the in-memory registry.
 */
@Mixin(TextureManager.class)
public class TextureManagerMixin {

    @Inject(
        method = "register(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V",
        at = @At("HEAD"),
        remap = true,
        require = 0
    )
    private void onRegisterTexture(Identifier location, AbstractTexture texture, CallbackInfo ci) {
        if (!ASTCCapabilities.isLdrSupported()) return;
        if (ASTCTextureRegistry.has(location)) return; // already registered

        // Check disk cache
        ASTCFormatSelector.FormatInfo fmt = ASTCFormatSelector.select(location);
        byte[] cached = ASTCTextureCache.lookup(location, fmt.blockX(), fmt.blockY());
        if (cached != null) {
            ASTCTextureRegistry.put(location,
                new ASTCTextureRegistry.ASTCEntry(cached, fmt.vkFormat(), fmt.blockX(), fmt.blockY()));
            NexusASTCPack.LOGGER.debug("[NexusASTCPack] Cache hit on register: {}", location);
        }
    }
}
