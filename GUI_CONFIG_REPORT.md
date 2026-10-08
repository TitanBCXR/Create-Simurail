# Lightweight Physics GUI Configuration - Feature Report

## Summary

Added a custom in-game GUI for configuring lightweight physics settings, styled with Jared's Dex5 theme. The screen features a transparent background, custom controls, and optional branding support for personal builds.

## Artifacts

### Default Build (Upstream-Friendly)
**File**: `/opt/cursor/artifacts/simurail-0.1.0+mc1.21.1-pr1-gui.jar`  
**Size**: 1.1M  
**Logo**: No watermark

### Titan Branded Build (Jared's Personal Build)
**File**: `/opt/cursor/artifacts/simurail-0.1.0+mc1.21.1-pr1-gui-titan.jar`  
**Size**: 1.6M  
**Logo**: Titan ouroboros watermark at 12% alpha

**Build command for branded version**:
```bash
./gradlew clean build -PtitanBranding=true
```

## How to Open the GUI

### Method 1: NeoForge Mods Menu
1. Launch game
2. Go to Mods menu
3. Find "Simurail" in the list
4. Click the "Config" button

### Method 2: Keybind (In-Game)
- **Default**: Unbound (user must set in Controls menu)
- **Key name**: "Open Lightweight Physics Config"
- **Category**: "Simurail"
- **Suggested binding**: P for Physics, or any preferred key

To bind:
1. Options → Controls → Key Binds
2. Scroll to "Simurail" category
3. Click "Open Lightweight Physics Config"
4. Press desired key
5. Press in-game to open config screen

## GUI Features

### Controls

1. **Lightweight Physics Toggle** (Big ON/OFF button at top)
   - Green when ON
   - Grey when OFF
   - 200x40 pixels, prominent placement

2. **Activation Radius Slider**
   - Range: 0-256 meters
   - Default: 32.0
   - Controls distance from trains where items activate

3. **Max Active Objects Slider**
   - Range: 0-2048
   - Default: 256
   - Hard cap on simultaneous tracked items

4. **Update Interval Slider**
   - Range: 1-20 ticks
   - Default: 4
   - How often items check for sublevels

5. **Sleep Velocity Slider**
   - Range: 0-10 m/s
   - Default: 0.05
   - Velocity threshold for optimization (currently unused but kept for future)

6. **Debug Logging Toggle**
   - Default: OFF
   - Enables tracking attachment/detachment logs

7. **Action Buttons**
   - **Reset**: Restores all values to defaults
   - **Save**: Applies changes (pink accent color)
   - **Cancel**: Discards changes and closes

### Visual Design (Dex5 Theme)

**Color Palette** (Jared's exact spec):
- Primary: `#00BFFF` (deep sky blue) - borders, progress bars
- Accent: `#FF1493` (deep pink) - Save button, hover states
- Background: `#0A0A0F` with transparency - shows game world
- Surface: `#1A1A2E` at 80% alpha - semi-transparent panel
- Text: `#FFFFFF` (white) - primary text
- Text Secondary: `#B8B8D0` (light grey-purple) - labels
- Success: `#00FF88` (bright green) - ON toggle state
- Error: `#FF4444` (bright red) - error messages
- Warning: `#FFB300` (amber) - read-only notice

**Layout**:
- Transparent background with 20% black dim over game world
- Centered semi-transparent panel (420px width max)
- Accent border around panel
- Readable at GUI scales 2-4
- Modern, high-contrast design

**Watermark** (Titan branded build only):
- 1024x1024 pink-to-teal ouroboros + TITAN text
- Rendered at 60% of panel width
- Centered in panel
- 12% alpha (barely visible, doesn't interfere with controls)
- Only shows when `-DsimurailTitanBranding=true` system property set at runtime

## Configuration Synchronization

### Singleplayer
- Changes apply **immediately** to running config
- Written to `config/simurail-server.toml`
- No restart required (config reloads live)
- All controls active and editable

### Multiplayer
- **Operators (level 2+)**:
  - Can edit all values
  - Changes sent via C2S packet with permission check
  - Server validates values against ranges
  - Updates applied to server config live
  - Success message in chat
  
- **Non-Operators**:
  - **Read-only mode**
  - All controls greyed out/disabled
  - Can view current values
  - Yellow warning: "Read-only: Requires operator permissions"
  - Cannot save changes

### Security
- Server-side permission check (`player.hasPermissions(2)`)
- All values validated against config min/max ranges
- Malicious packets rejected with error message
- No client-side trust - server validates everything

## Technical Implementation

### New Files

**Client GUI**:
- `src/main/java/com/crystaelix/simurail/client/config/`
  - `Dex5Colors.java` - Color palette constants
  - `LightweightPhysicsConfigScreen.java` - Main GUI screen (350 lines)
  - `Dex5ToggleButton.java` - Custom ON/OFF toggle
  - `Dex5Slider.java` - Custom slider with value display
  - `LightweightPhysicsConfigValues.java` - Config snapshot wrapper

**Client Events**:
- `src/main/java/com/crystaelix/simurail/client/`
  - `SimurailKeyMappings.java` - Keybind registration
  - `SimurailClientEvents.java` - Keybind handling

**Networking**:
- `src/main/java/com/crystaelix/simurail/network/`
  - `SimurailPackets.java` - Packet registry
  - `UpdateLightweightPhysicsConfigPacket.java` - C2S config update packet

**Branding** (separate source set):
- `src/titan/resources/assets/simurail/textures/gui/titan_logo_watermark.png` (1024x1024, 539 KB)

### Modified Files

- `SimurailClient.java` - Registered `IConfigScreenFactory` for GUI
- `build.gradle` - Added titan source set, conditional resource inclusion
- `en_us.json` - Added keybind translation keys

### No Mixins
- **Zero mixins added** for this feature
- Uses pure packet-based networking
- Native NeoForge APIs only
- No bytecode manipulation required

### Upstream-Friendly Design

**Default Build** (no `-PtitanBranding`):
- No logo texture included
- Standard 1.1M jar size
- Clean for upstream contribution
- No personal branding

**Branded Build** (with `-PtitanBranding=true`):
- Logo included from `src/titan/resources/`
- Larger 1.6M jar (includes 539KB PNG)
- Only Jared's personal builds
- Runtime check for texture existence

**Graceful Degradation**:
- If logo texture missing, GUI renders without it
- No errors, no crashes
- System property check: `-DsimurailTitanBranding=true`
- Texture existence check at runtime

## What Applies Live vs. After Reload

### Applies Immediately (No Restart)
✅ **enabled** - Physics on/off toggle  
✅ **updateInterval** - Check frequency  
✅ **debugLogging** - Debug output  
✅ **activationRadius** - Proximity distance  
✅ **maxActive** - Object cap  
✅ **sleepVelocity** - Sleep threshold  

All values are read directly from config objects that are checked every tick/update, so changes take effect as soon as the packet updates the server config.

### No Reload Required
The `SimurailConfig.server().physics` values are accessed dynamically by:
- `ItemEntityPhysicsMixin` - reads `enabled`, `updateInterval`, `debugLogging`
- `LightweightPhysicsManager` - reads all values during tick

**Result**: Toggle physics in-game, see items start/stop tracking trains immediately.

## Multiplayer Behavior

### Scenario 1: Player is OP
```
1. Open GUI (Mods menu or keybind)
2. All controls active and editable
3. Adjust values
4. Click Save
5. Packet sent to server with new values
6. Server checks permission (✓ level 2+)
7. Server validates ranges (✓ all in bounds)
8. Server applies to config
9. Chat message: "§aLightweight physics config updated successfully"
10. Changes take effect immediately on server
```

### Scenario 2: Player is Not OP
```
1. Open GUI (Mods menu or keybind)
2. All controls greyed out/disabled
3. Current server values displayed (read-only)
4. Yellow warning: "Read-only: Requires operator permissions"
5. Cannot click Save (disabled)
6. Can only Cancel to close
```

### Scenario 3: Non-OP Tries to Hack
```
1. Malicious client sends packet anyway
2. Server receives packet
3. Permission check fails (level < 2)
4. Server sends error: "§cError: You need operator permissions to change server config"
5. Config unchanged
6. Packet rejected
```

### Scenario 4: OP Sends Invalid Values
```
1. Modified client sends out-of-range values
2. Server receives packet
3. Permission check passes (level 2+)
4. Validation fails (e.g. maxActive = 9999 > 2048)
5. Server sends error: "§cError: Invalid config values (out of range)"
6. Config unchanged
7. Packet rejected
```

## Testing the GUI

### In Singleplayer
1. Install either jar variant
2. Launch game, load world
3. Open GUI (Mods menu → Simurail → Config, or keybind)
4. Toggle physics ON/OFF
5. Adjust sliders
6. Enable debug logging
7. Click Save
8. Drop items on moving train
9. Check logs for debug output (if enabled)

### In Multiplayer (as OP)
1. Join server as operator
2. Open GUI
3. Should see all controls active
4. Make changes, click Save
5. Check chat for success message
6. Test with items on trains

### In Multiplayer (as Non-OP)
1. Join server without op
2. Open GUI
3. Should see read-only mode
4. All controls greyed out
5. Yellow warning message visible
6. Can only view current values

## Known Limitations

1. **Keybind unbound by default** - User must bind it manually in Controls
2. **Screenshot not provided** - GUI can only be rendered in running client
3. **Watermark requires system property** - Must set `-DsimurailTitanBranding=true` at runtime for logo to show
4. **Read-only values in MP** - Non-ops cannot see "effective" values if server differs from default

## Build Instructions

### Default (Upstream)
```bash
./gradlew clean build
# Output: simurail-0.1.0+mc1.21.1.jar (1.1M, no logo)
```

### Titan Branded
```bash
./gradlew clean build -PtitanBranding=true
# Output: simurail-0.1.0+mc1.21.1.jar (1.6M, with logo)

# To see logo at runtime, also set:
java -DsimurailTitanBranding=true -jar ...
# Or in launcher JVM args: -DsimurailTitanBranding=true
```

## File Organization

```
src/
  main/
    java/com/crystaelix/simurail/
      client/
        SimurailClientEvents.java          [NEW]
        SimurailKeyMappings.java           [NEW]
        config/
          Dex5Colors.java                  [NEW]
          Dex5Slider.java                  [NEW]
          Dex5ToggleButton.java            [NEW]
          LightweightPhysicsConfigScreen.java [NEW]
          LightweightPhysicsConfigValues.java [NEW]
      network/
        SimurailPackets.java               [NEW]
        UpdateLightweightPhysicsConfigPacket.java [NEW]
      SimurailClient.java                  [MODIFIED]
    resources/assets/simurail/lang/
      en_us.json                           [MODIFIED]
  
  titan/                                   [NEW - separate source set]
    resources/assets/simurail/textures/gui/
      titan_logo_watermark.png             [NEW - 1024x1024, 539KB]

build.gradle                               [MODIFIED]
```

**Titan Source Set**: Only included when `-PtitanBranding=true` is passed to Gradle. Not committed to default source tree, keeps upstream clean.

## Verification Checklist

✅ Default jar built successfully (1.1M, no logo)  
✅ Titan jar built successfully (1.6M, with logo)  
✅ Both jars saved to `/opt/cursor/artifacts/`  
✅ Logo verified in titan jar: `unzip -l` shows `titan_logo_watermark.png`  
✅ Default jar has no logo texture  
✅ Build passes with no errors (4 deprecation warnings, harmless)  
✅ No mixins added  
✅ Code compiles with no errors  
✅ Packet registered correctly  
✅ GUI screen registered to NeoForge config factory  
✅ Keybind registered (unbound by default)  
✅ Lang keys added for keybind  
✅ Titan source set only included with build flag  
✅ Changes committed to branch  
✅ Changes pushed to PR branch  

## What Jared Should Test

### Basic GUI Test
1. Install `simurail-0.1.0+mc1.21.1-pr1-gui.jar` (or titan variant)
2. Launch game
3. Go to Mods menu → Simurail → Config button
4. **Expected**: Dex5-styled GUI opens with transparent background
5. **Expected**: All controls visible and editable (singleplayer)
6. **Expected**: Toggle physics, adjust sliders, enable debug
7. Click Save
8. **Expected**: Status message "Saved successfully" in green
9. Drop items on moving train
10. **Expected**: Items ride along (from previous fix)
11. **Expected**: If debug enabled, see tracking logs in latest.log

### Keybind Test
1. Options → Controls → Key Binds
2. Scroll to "Simurail" category
3. Bind "Open Lightweight Physics Config" to a key (e.g. P)
4. In-game, press the key
5. **Expected**: GUI opens

### Branding Test (Titan Jar Only)
1. Install `simurail-0.1.0+mc1.21.1-pr1-gui-titan.jar`
2. Add to launcher JVM args: `-DsimurailTitanBranding=true`
3. Launch game
4. Open config GUI
5. **Expected**: Faint Titan logo watermark visible behind controls (12% alpha)
6. **Expected**: Logo doesn't interfere with readability

### Multiplayer Test (If Possible)
1. Join a server
2. Open GUI without OP
3. **Expected**: Read-only mode, yellow warning
4. Get OP (`/op <name>`)
5. Reopen GUI
6. **Expected**: Editable mode
7. Make changes, Save
8. **Expected**: Success message in chat
9. Check server logs for config update

## Next Steps

1. Jared tests GUI in-game
2. If any visual/UX issues, report for iteration
3. If logo needs different alpha or positioning, adjust
4. If keybind should have a default, can set one
5. Once approved, ready to merge with other PR changes

---

**Report Date**: October 8, 2026  
**Feature**: Dex5 GUI Config Screen for Lightweight Physics  
**PR**: https://github.com/TitanBCXR/Create-Simurail/pull/1  
**Branch**: `cursor/non-multiblock-physics-2acb`  
**Commit**: 0f7edcd
