# Titan Branding Simplification

## Change

**Removed JVM argument requirement** - Titan watermark now shows automatically when texture exists in jar.

## How It Works

- **Default jar**: No texture → no logo
- **Titan jar**: Texture included → logo shows automatically
- **Detection**: Checks if `titan_logo_watermark.png` resource exists at runtime
- **No config needed**: Works out of box in CurseForge

## Build Commands

```bash
# Default (no logo)
./gradlew clean build

# Titan (with logo)
./gradlew clean build -PtitanBranding=true
```

## Artifacts

Both jars rebuilt and saved to `/opt/cursor/artifacts/`:

| File | Size | Logo | Mod ID | Version |
|------|------|------|--------|---------|
| `simurail-0.1.0+mc1.21.1-pr1-gui.jar` | 1.1M | No | simurail | 0.1.0+mc1.21.1 |
| `simurail-0.1.0+mc1.21.1-pr1-gui-titan.jar` | 1.6M | Yes | simurail | 0.1.0+mc1.21.1 |

**Verified**: Both jars have identical `mods.toml` - no duplicate mod issues.

## Usage

1. Download either jar
2. Install in CurseForge
3. Launch game
4. **Titan jar**: Logo appears automatically (no JVM args needed)
5. **Default jar**: No logo

**Branch**: `cursor/non-multiblock-physics-2acb`  
**Commit**: 9655499
