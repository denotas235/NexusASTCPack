#!/usr/bin/env bash
# convert_textures.sh — Converts Minecraft 1.21.11 vanilla textures to ASTC
#
# Usage: convert_textures.sh <mc_client.jar> <astcenc_binary> <output_textures_dir> <index_file>
#
# Called by Gradle's convertASTCTextures task.
# Also callable standalone for manual / CI use.
#
# Encoder quality: -thorough (maximum — runs once at build time, not at runtime)
# ARM astcenc: https://github.com/ARM-software/astc-encoder

set -euo pipefail

MC_JAR="${1:-}"
ASTCENC="${2:-}"
OUT_TEXTURES_DIR="${3:-src/main/resources/assets/minecraft/textures}"
INDEX_FILE="${4:-src/main/resources/assets/nexusastcpack/astc_index.json}"

# ── Validation ─────────────────────────────────────────────────────────────
if [[ -z "$MC_JAR" || ! -f "$MC_JAR" ]]; then
  echo "[ASTC] ERROR: MC client JAR not found: '$MC_JAR'"
  exit 0   # Non-fatal: build continues without bundled textures
fi

if [[ -z "$ASTCENC" || ! -x "$ASTCENC" ]]; then
  echo "[ASTC] ERROR: astcenc binary not executable: '$ASTCENC'"
  exit 0
fi

echo "[ASTC] MC JAR   : $MC_JAR"
echo "[ASTC] Encoder  : $ASTCENC ($("$ASTCENC" --version 2>&1 | head -1))"
echo "[ASTC] Output   : $OUT_TEXTURES_DIR"

# ── Extract textures from MC JAR ──────────────────────────────────────────
TMP_EXTRACT=$(mktemp -d)
trap 'rm -rf "$TMP_EXTRACT"' EXIT

echo "[ASTC] Extracting textures from JAR..."
(cd "$TMP_EXTRACT" && jar xf "$MC_JAR" assets/minecraft/textures/) 2>/dev/null || true

PNG_COUNT=$(find "$TMP_EXTRACT/assets/minecraft/textures" -name "*.png" 2>/dev/null | wc -l)
echo "[ASTC] Found $PNG_COUNT PNG textures"

if [[ "$PNG_COUNT" -eq 0 ]]; then
  echo "[ASTC] No textures found in JAR — aborting"
  exit 0
fi

# ── Format selection ───────────────────────────────────────────────────────
get_block_size() {
  local path="$1"
  # Normal maps → 4x4 linear
  if [[ "$path" =~ _n\.png$ ]] || [[ "$path" =~ normal ]]; then echo "4x4"; return; fi
  # GUI, font, items → 4x4 (sharpness critical)
  if [[ "$path" =~ /gui/ ]] || [[ "$path" =~ /font/ ]] || [[ "$path" =~ /item/ ]] || [[ "$path" =~ /items/ ]]; then echo "4x4"; return; fi
  # Environment (sun, moon) → 4x4
  if [[ "$path" =~ /environment/ ]] || [[ "$path" =~ /sun ]] || [[ "$path" =~ /moon ]]; then echo "4x4"; return; fi
  # Transparent blocks (glass, leaves, ice) → 4x4 (alpha fidelity)
  if [[ "$path" =~ glass ]] || [[ "$path" =~ leaves ]] || [[ "$path" =~ ice ]]; then echo "4x4"; return; fi
  # Distant terrain → 8x8
  if [[ "$path" =~ terrain ]] || [[ "$path" =~ /distant/ ]]; then echo "8x8"; return; fi
  # Default: blocks, entities, particles → 6x6
  echo "6x6"
}

get_vkformat() {
  local block="$1"
  # Using sRGB formats for colour-correct rendering
  case "$block" in
    "4x4") echo 158 ;;   # VK_FORMAT_ASTC_4x4_SRGB_BLOCK
    "6x6") echo 166 ;;   # VK_FORMAT_ASTC_6x6_SRGB_BLOCK
    "8x8") echo 172 ;;   # VK_FORMAT_ASTC_8x8_SRGB_BLOCK
    *)     echo 166 ;;
  esac
}

# ── Convert ────────────────────────────────────────────────────────────────
mkdir -p "$(dirname "$INDEX_FILE")"
CONVERTED=0
FAILED=0
INDEX_ENTRIES=""

while IFS= read -r PNG_PATH; do
  # Skip animated texture mcmeta companions and .mcmeta files
  [[ "$PNG_PATH" == *.mcmeta ]] && continue

  # Path relative to TMP_EXTRACT: assets/minecraft/textures/block/stone.png
  REL="${PNG_PATH#$TMP_EXTRACT/}"                     # assets/minecraft/textures/block/stone.png
  REL_NO_ASSETS="${REL#assets/minecraft/}"            # textures/block/stone.png
  REL_NO_EXT="${REL_NO_ASSETS%.png}"                  # textures/block/stone
  TEXTURE_ID="minecraft:${REL_NO_EXT}"                # minecraft:textures/block/stone

  BLOCK=$(get_block_size "$PNG_PATH")
  VK_FMT=$(get_vkformat "$BLOCK")
  BX="${BLOCK%x*}"
  BY="${BLOCK#*x}"

  ASTC_OUT="$OUT_TEXTURES_DIR/${REL_NO_EXT}.astc"
  mkdir -p "$(dirname "$ASTC_OUT")"

  # Skip if already converted (incremental builds)
  if [[ -f "$ASTC_OUT" && "$ASTC_OUT" -nt "$PNG_PATH" ]]; then
    CONVERTED=$((CONVERTED + 1))
    INDEX_ENTRIES="${INDEX_ENTRIES}{\"texture\":\"${TEXTURE_ID}\",\"vkFormat\":${VK_FMT},\"blockSizeX\":${BX},\"blockSizeY\":${BY}},"
    continue
  fi

  if "$ASTCENC" -cl "$PNG_PATH" "$ASTC_OUT" "$BLOCK" -thorough 2>/dev/null; then
    CONVERTED=$((CONVERTED + 1))
    INDEX_ENTRIES="${INDEX_ENTRIES}{\"texture\":\"${TEXTURE_ID}\",\"vkFormat\":${VK_FMT},\"blockSizeX\":${BX},\"blockSizeY\":${BY}},"
  else
    FAILED=$((FAILED + 1))
    echo "[ASTC] FAILED: $REL"
  fi
done < <(find "$TMP_EXTRACT/assets/minecraft/textures" -name "*.png" | sort)

# ── Write index ────────────────────────────────────────────────────────────
# Remove trailing comma and wrap in JSON
INDEX_ENTRIES="${INDEX_ENTRIES%,}"
cat > "$INDEX_FILE" << INDEXEOF
{
  "version": 1,
  "entries": [${INDEX_ENTRIES}]
}
INDEXEOF

echo "[ASTC] Done — converted: $CONVERTED  failed: $FAILED"
echo "[ASTC] Index written: $INDEX_FILE"
