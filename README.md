# NexusASTCPack

**ASTC texture compression for VulkanMod on Mali-G52 MC2**

A standalone Fabric mod for Minecraft 1.21.11 that provides ASTC-compressed vanilla textures,
intercepts resource loading to serve `.astc` instead of `.png`, and caches runtime conversions
of third-party mod textures.

## Target hardware

| Property | Value |
|---|---|
| Device | TECNO KH7 |
| GPU | Mali-G52 MC2 |
| Vulkan | 1.1.177 |
| Android | 12 arm64 |
| Launcher | Zalith Launcher |

## Dependencies

- **Required**: [VulkanMod](https://github.com/denotas235/NEXUSVULKANMOD) (for Vulkan backend)
- **Minecraft**: 1.21.11
- **Fabric Loader**: ≥ 0.19.2

## How it works

### Phase 1 (this release)
1. Detects Vulkan ASTC LDR/HDR support via `DeviceManager.device.availableFeatures`
2. Loads pre-converted ASTC textures from the JAR into `ASTCTextureRegistry`
3. Caches runtime conversions to `.minecraft/astc-cache/`
4. Intercepts `TextureAtlas.upload()` and `SpriteLoader.stitch()` to log ASTC readiness

### Phase 2 (planned — requires NEXUSVULKANMOD update)
- NEXUSVULKANMOD reads `ASTCTextureRegistry.get(location)` before creating VkImages
- Textures with ASTC data are uploaded as `VK_FORMAT_ASTC_*` instead of `VK_FORMAT_R8G8B8A8_UNORM`
- Expected GPU memory reduction: ~60-75% for opaque textures

## ASTC format selection

| Texture category | Format | VkFormat |
|---|---|---|
| GUI, font, items | ASTC 4×4 sRGB | 158 |
| Transparent blocks | ASTC 4×4 sRGB | 158 |
| Blocks (opaque), entities | ASTC 6×6 sRGB | 166 |
| Distant terrain | ASTC 8×8 sRGB | 172 |
| Normal maps | ASTC 4×4 UNORM | 157 |
| Environment (HDR) | ASTC 4×4 SFLOAT | 1000066000 |

## Building with bundled textures

```bash
# Install astcenc (ARM official encoder)
curl -L "https://github.com/ARM-software/astc-encoder/releases/download/4.8.0/astcenc-4.8.0-linux-x86_64.zip" \
  -o astcenc.zip && unzip astcenc.zip

# Build with texture conversion
ASTCENC_PATH=./bin/astcenc-avx2 ./gradlew build
```

## Public API

Other mods can query ASTC data via:

```java
if (FabricLoader.getInstance().isModLoaded("nexusastcpack")) {
    ASTCTextureRegistry.ASTCEntry entry = ASTCTextureRegistry.get(resourceLocation);
    if (entry != null) {
        // entry.data()    — raw ASTC bytes for Vulkan upload
        // entry.vkFormat() — VkFormat constant
        // entry.blockX(), entry.blockY() — block dimensions
    }
}
```
