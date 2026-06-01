package net.vulkanmod.astc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class NexusASTCPack implements ClientModInitializer {

    public static final String MOD_ID  = "nexusastcpack";
    public static final Logger LOGGER  = LoggerFactory.getLogger("NexusASTCPack");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[NexusASTCPack] Initialising ASTC texture compression mod");

        // Best-effort early detection (Vulkan device may not be ready yet)
        ASTCCapabilities.detect();

        if (ASTCCapabilities.isLdrSupported()) {
            LOGGER.info("[NexusASTCPack] Mali ASTC LDR supported — textures will use ASTC");
            LOGGER.info("[NexusASTCPack] HDR={} DecodeMode={}",
                    ASTCCapabilities.isHdrSupported(), ASTCCapabilities.isDecodeModeSupported());
        } else {
            LOGGER.info("[NexusASTCPack] ASTC not detected yet — will re-check at resource load");
        }

        // Initialise disk cache (.minecraft/astc-cache/)
        ASTCTextureCache.init();

        // Register reload listener: populates ASTCTextureRegistry from bundled .astc files
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new ASTCResourceProvider());

        LOGGER.info("[NexusASTCPack] Registered resource reload listener");
    }
}
