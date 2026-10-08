# Item Physics Fix Report - PR#1 Fix #2

## Executive Summary

**Status**: Fixed and ready for testing  
**Build**: `simurail-0.1.0+mc1.21.1-pr1-fix2.jar`  
**Branch**: `cursor/non-multiblock-physics-2acb`  
**Issue**: Items dropped on moving trains fell off instead of riding along

## Root Cause Analysis

### The Problem

The original implementation used `AttachableBoxPhysicsObject` to create physics bodies inside the train's `ServerSubLevel`. However, this approach had a fundamental architectural flaw:

1. **Items exist in world space**, not sublevel space
2. **Train blocks exist in a ServerSubLevel** with its own coordinate system and transform
3. The `AttachableBoxPhysicsObject` created a physics body *inside the sublevel's physics simulation*
4. The world-space `ItemEntity` was never connected to that physics body
5. The item entity's position/velocity is managed by vanilla `Entity.tick()` and `move()`, which don't know about sublevel physics bodies

**Result**: Items had no way to inherit the train's motion and fell off.

### Why It Failed Silently

- The mixin loaded without errors ✓
- The manager registered sublevels ✓
- Physics bodies were created ✓
- **But**: The item entity in world space was never moved by the sublevel-space physics body ✗

The system was working internally but had no bridge between sublevel physics and world-space entity movement.

### The Discovery

While investigating how Sable/Simulated/Aeronautics handle players and mobs on moving trains, I discovered:

```java
// From Sable's SubLevelEntityCollision.collide()
ServerSubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);
if (trackingSubLevel != null) {
    entity.setOnGround(true);
    // Entity inherits sublevel movement automatically
}
```

**Sable already has a built-in system for entities riding sublevels!**

Players and mobs use `sable$setTrackingSubLevel` to "attach" to a moving sublevel. The collision system then automatically:
- Keeps them on the ground relative to the sublevel
- Applies the sublevel's movement to their velocity
- Handles coordinate transforms between sublevel and world space

## The Solution

### New Architecture

Instead of custom physics bodies, use Sable's proven sublevel tracking mechanism:

```java
@Mixin(ItemEntity.class)
public abstract class ItemEntityPhysicsMixin implements EntityMovementExtension {
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void simurail$onTick(CallbackInfo ci) {
        // 1. Check if item is on ground
        if (!self.onGround() || !self.verticalCollision) return;
        
        // 2. Find sublevel below the item
        ServerSubLevel standingSubLevel = findStandingSubLevel(self);
        ServerSubLevel currentTracking = sable$getTrackingSubLevel();
        
        // 3. Update tracking
        if (standingSubLevel != currentTracking) {
            sable$setTrackingSubLevel(standingSubLevel); // or null to detach
        }
    }
}
```

### How It Works

1. **Detection**: Sample positions below the item's bounding box
2. **Query**: Use `Sable.HELPER.getContaining(level, blockPos)` to find sublevels
3. **Attachment**: Call `sable$setTrackingSubLevel(sublevel)` to attach the item
4. **Automatic Movement**: Sable's `SubLevelEntityCollision` handles the rest

### Key Benefits

- **Upstream-aligned**: Uses the same mechanism Sable uses for players/mobs
- **Proven**: This system already works for all other entities
- **Simple**: No custom physics bodies, velocity blending, or coordinate transforms
- **Performant**: Only checks occasionally (configurable interval)
- **Maintainable**: Less custom code = fewer bugs

## Implementation Details

### Files Modified

1. **`src/main/java/com/crystaelix/simurail/mixin/ItemEntityPhysicsMixin.java`**
   - Complete rewrite to use sublevel tracking
   - Implements `EntityMovementExtension` mixin interface from Sable
   - Detects sublevels below items and attaches/detaches as needed
   
2. **`src/main/java/com/crystaelix/simurail/config/SimurailPhysicsConfig.java`**
   - Added `lightweightDebugLogging` boolean (default: false)
   - Updated config comments to reflect simplified implementation

### Config Changes

New option in `[physics.lightweight]`:

```toml
[physics.lightweight]
    debugLogging = false
```

When `debugLogging = true`, logs:
- When an item starts tracking a sublevel (attaches to train)
- When an item stops tracking a sublevel (detaches from train)
- Item ID, sublevel UUID, and position

Example log output:
```
[Simurail] [Lightweight Physics] Item 12345 starting to track sublevel 550e8400-e29b-41d4-a716-446655440000 at (100.5, 64.0, 200.3)
[Simurail] [Lightweight Physics] Item 12345 stopped tracking sublevel 550e8400-e29b-41d4-a716-446655440000
```

### Performance Characteristics

- **Check interval**: Configurable via `lightweightUpdateInterval` (default: 4 ticks)
- **Early exits**: Skips client-side, removed entities, young items (<10 ticks), water, not on ground
- **Spatial sampling**: Only checks ~4-8 positions below the item
- **No continuous physics simulation**: Only attachment/detachment checks

### Mixin Verification

**Target**: `ItemEntity.tick()`  
**Descriptor**: `()V`  
**Status**: ✅ Verified with javap against Mojang-mapped jar  
**Safety**: Method is declared on `ItemEntity` (not inherited), safe for production without refmap

```bash
$ javap -s ItemEntity.class | grep "public void tick()"
  public void tick();
    descriptor: ()V
```

## Testing Instructions

### For Jared (CurseForge Test)

1. **Replace the jar**:
   - Remove previous version
   - Install `simurail-0.1.0+mc1.21.1-pr1-fix2.jar`

2. **Enable debug logging** (optional but recommended):
   - Edit `config/simurail-server.toml`
   - Under `[physics.lightweight]`, set `debugLogging = true`

3. **Run the test**:
   ```
   a) Start the game and load the test world
   b) Build a train with Physics Bogeys (or use existing test train)
   c) Assemble and power the train so it moves
   d) Drop items (diamonds, iron, blocks, etc.) onto the moving train
   e) Observe: items should ride along with the train
   ```

4. **Expected behavior**:
   - ✅ Items dropped onto moving trains **stay on the train**
   - ✅ Items **move with the train** as it travels
   - ✅ Items can be picked up normally while on the train
   - ✅ When train stops, items remain on it
   - ✅ If item falls off edge of train, it stops tracking and falls normally

5. **Expected debug log output** (if enabled):
   ```
   [Simurail] [Lightweight Physics] Item 12345 starting to track sublevel <UUID> at (x, y, z)
   ```
   - Should appear shortly after dropping an item onto a moving train
   - One line per item that attaches
   - "stopped tracking" message when item despawns or falls off

6. **What to report back**:
   - Does it work? (items ride trains?)
   - Any errors in latest.log?
   - Any unexpected behavior?
   - Debug log output (if enabled)

### Advanced Testing (Optional)

- **Multi-car trains**: Test items on different cars
- **Fast trains**: Test at high speeds
- **Turning trains**: Test on curved tracks
- **Vertical movement**: Test on slopes/elevators
- **Item stacks**: Drop multiple item stacks at once
- **Different item types**: Test blocks vs. small items
- **Train stopping**: Drop items, let train stop, pick them up

## Technical Notes

### Why This Approach Is Correct

1. **Coordinate System**: Sable handles the complex coordinate transforms between world space and sublevel space
2. **Collision Detection**: `SubLevelEntityCollision.collide()` already has optimized collision with sublevel blocks
3. **Movement Inheritance**: The tracking system properly applies the sublevel's velocity and rotation
4. **Edge Cases**: Sable's implementation handles entering/exiting sublevels, teleportation, etc.

### Differences from Previous Approach

| Aspect | Old (AttachableBoxPhysicsObject) | New (Sublevel Tracking) |
|--------|----------------------------------|-------------------------|
| Physics Bodies | Created in sublevel | None needed |
| Velocity Application | Manual blending | Automatic via Sable |
| Coordinate Transforms | Not handled | Handled by Sable |
| Collision Detection | Custom AABB checks | Uses SubLevelEntityCollision |
| Complexity | High (custom physics system) | Low (reuse existing system) |
| Alignment | Custom implementation | Uses upstream Sable API |

### Known Limitations

1. **Only items on ground**: Items in mid-air don't attach (intentional, matches player behavior)
2. **Water disabled**: Items in water don't track sublevels (prevents weird water physics)
3. **Update delay**: Items check for sublevels every N ticks, so brief delay before attachment
4. **Young items skipped**: Items <10 ticks old don't attach (lets them settle after spawning)

All of these are intentional design choices to prevent edge cases and match vanilla/Sable behavior.

## Verification Checklist

- ✅ Code compiles without errors
- ✅ Mixin target verified with javap
- ✅ Jar built successfully: `simurail-0.1.0+mc1.21.1-pr1-fix2.jar`
- ✅ Jar copied to `/opt/cursor/artifacts/`
- ✅ Changes committed to branch `cursor/non-multiblock-physics-2acb`
- ✅ Changes pushed to remote
- ✅ PR #1 updated with new commit
- ⏳ In-game testing pending (awaiting Jared's test)

## Next Steps

1. Jared tests the new jar in CurseForge
2. If items ride trains correctly → Success! 🎉
3. If issues remain → Analyze debug logs and investigate further
4. Once confirmed working → Ready to merge PR #1

## Appendix: Code Comparison

### Old Approach (Broken)
```java
// Created physics body in sublevel
LightweightPhysicsBody body = new LightweightPhysicsBody(...);
AttachableBoxPhysicsObject obj = new AttachableBoxPhysicsObject(sublevel, ...);
// ❌ World-space item entity never connected to sublevel-space physics body
Vec3 physicsVel = body.getVelocityVec3();
self.setDeltaMovement(currentVel.blend(physicsVel)); // Manual blending
```

### New Approach (Correct)
```java
// Use Sable's tracking system
ServerSubLevel sublevel = findStandingSubLevel(item);
sable$setTrackingSubLevel(sublevel);
// ✅ Sable's SubLevelEntityCollision automatically handles everything
```

---

**Report generated**: October 8, 2026  
**Agent**: Cursor Cloud Agent  
**PR**: https://github.com/TitanBCXR/Create-Simurail/pull/1
