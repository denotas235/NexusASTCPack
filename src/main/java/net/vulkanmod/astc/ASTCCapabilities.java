package net.vulkanmod.astc;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Detects ASTC texture compression support via VulkanMod's DeviceManager.
 * Uses reflection so that VulkanMod remains an optional (soft) dependency.
 *
 * Detection path:
 *   DeviceManager.device.availableFeatures.features().textureCompressionASTC_LDR()
 *   DeviceManager.device.supportedExtensions → VK_EXT_texture_compression_astc_hdr
 */
public class ASTCCapabilities {

    private static final Logger LOGGER = LoggerFactory.getLogger("NexusASTCPack");

    private static volatile boolean detected          = false;
    private static volatile boolean ldrSupported      = false;
    private static volatile boolean hdrSupported      = false;
    private static volatile boolean decodeModeSupported = false;

    /** Called early during mod init; safe to call repeatedly. */
    public static synchronized void detect() {
        if (detected) return;
        detected = true;

        if (!FabricLoader.getInstance().isModLoaded("vulkanmod")) {
            LOGGER.info("[NexusASTCPack] VulkanMod not present — ASTC disabled");
            return;
        }

        try {
            detectViaDeviceManager();
        } catch (ClassNotFoundException e) {
            LOGGER.debug("[NexusASTCPack] DeviceManager class not found: {}", e.getMessage());
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[NexusASTCPack] DeviceManager field changed: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("[NexusASTCPack] ASTC detection error: {}", e.getMessage());
        }
    }

    private static void detectViaDeviceManager() throws Exception {
        Class<?> devMgrClass = Class.forName("net.vulkanmod.vulkan.device.DeviceManager");

        // DeviceManager.device — the Device wrapper object
        Field deviceField = devMgrClass.getField("device");
        Object device = deviceField.get(null);
        if (device == null) {
            LOGGER.debug("[NexusASTCPack] DeviceManager.device is null — Vulkan not yet ready");
            return;
        }

        // Device.availableFeatures — VkPhysicalDeviceFeatures2
        Field featuresField = device.getClass().getField("availableFeatures");
        Object features2 = featuresField.get(device);
        if (features2 == null) return;

        // features2.features() → VkPhysicalDeviceFeatures
        Method featuresGetter = features2.getClass().getMethod("features");
        Object coreFeatures = featuresGetter.invoke(features2);

        // .textureCompressionASTC_LDR() → boolean
        Method ldrGetter = coreFeatures.getClass().getMethod("textureCompressionASTC_LDR");
        ldrSupported = (boolean) ldrGetter.invoke(coreFeatures);

        // Check extension list for HDR and DecodeMode
        try {
            Field extField = device.getClass().getDeclaredField("supportedExtensions");
            extField.setAccessible(true);
            Object extSet = extField.get(device);
            if (extSet instanceof java.util.Collection<?> coll) {
                for (Object ext : coll) {
                    String name = ext.toString();
                    if ("VK_EXT_texture_compression_astc_hdr".equals(name))  hdrSupported      = true;
                    if ("VK_EXT_astc_decode_mode".equals(name))               decodeModeSupported = true;
                }
            }
        } catch (Exception ignored) {
            // Extension list field may have been renamed — non-fatal
        }

        LOGGER.info("[NexusASTCPack] Vulkan ASTC — LDR={} HDR={} DecodeMode={}",
                ldrSupported, hdrSupported, decodeModeSupported);
    }

    /**
     * Force re-detection (call this after Vulkan device is confirmed ready,
     * e.g. from a resource reload triggered after startup).
     */
    public static synchronized void redetect() {
        detected         = false;
        ldrSupported     = false;
        hdrSupported     = false;
        decodeModeSupported = false;
        detect();
    }

    public static boolean isLdrSupported()       { return ldrSupported; }
    public static boolean isHdrSupported()       { return hdrSupported; }
    public static boolean isDecodeModeSupported(){ return decodeModeSupported; }
}
