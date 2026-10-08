# Mixin Fix: ItemEntityPhysicsMixin Production Load Issue

## Problem

The mod crashed on load in production (CurseForge) with:
```
InvalidInjectionException: Critical injection failure: @Inject annotation on simurail$onRemove 
could not find any targets matching 'remove' in net/minecraft/world/entity/item/ItemEntity. 
No refMap loaded.
```

**Root cause**: The `@Inject(method = "remove")` was targeting an **inherited method** from `Entity`, not a method declared on `ItemEntity` itself. Without a refMap (in production jars), mixins cannot resolve inherited methods.

## Fix

**Removed the problematic injection entirely**. The cleanup logic was already handled correctly in the `tick()` injection:

### Before (broken):
```java
@Inject(method = "tick", at = @At("HEAD"))
private void simurail$onTick(CallbackInfo ci) {
    if (!simurail$shouldHavePhysics()) {
        // Clean up physics body if conditions changed
        if (simurail$physicsBody != null) {
            LightweightPhysicsManager.get(serverLevel).remove(self);
            simurail$physicsBody = null;
        }
        return;
    }
    // ... update physics ...
}

@Inject(method = "remove", at = @At("HEAD"))  // ← BROKEN: 'remove' is inherited
private void simurail$onRemove(CallbackInfo ci) {
    // Redundant cleanup
}
```

### After (fixed):
```java
@Inject(method = "tick", at = @At("HEAD"))
private void simurail$onTick(CallbackInfo ci) {
    if (!simurail$shouldHavePhysics()) {  // ← checks isRemoved()
        // Clean up physics body if conditions changed
        if (simurail$physicsBody != null) {
            LightweightPhysicsManager.get(serverLevel).remove(self);
            simurail$physicsBody = null;
        }
        return;
    }
    // ... update physics ...
}
// No separate remove injection needed - cleanup is automatic
```

The `simurail$shouldHavePhysics()` method already checks `!self.isRemoved()`, so when an entity is removed, the next tick will clean up the physics body automatically. This is safer than a separate `remove()` hook anyway, since it handles all removal cases.

## Why This Works

1. **Targets declared method**: `tick()` is declared on `ItemEntity`, not inherited
2. **No redundant logic**: Cleanup happens naturally when entity is removed
3. **Production-safe**: Works without refmap (uses official/Mojang mappings)
4. **More robust**: Catches all removal cases, not just explicit `remove()` calls

## Other Mixins Audited

All other Simurail mixins were checked and are safe:
- They target methods from Create mod classes (TrackGraph, TrackBlockEntity, etc.)
- Or use accessor mixins (@Accessor/@Invoker)
- Or target methods known to be declared on the target class

## Verification

✅ **Compiles**: `./gradlew build` passes  
✅ **Mixin in jar**: Verified `ItemEntityPhysicsMixin.class` exists in production jar  
✅ **No `simurail$onRemove`**: Removed method not in compiled class  
⚠️ **Runtime test**: Dev server requires network access (unavailable in cloud environment)  

**Recommendation**: Test the built jar in the same CurseForge environment to verify it loads.

## Files Changed

- `src/main/java/com/crystaelix/simurail/mixin/ItemEntityPhysicsMixin.java`: Removed broken `@Inject(method = "remove")`
- `build.gradle`: Added mixin debug flags to run configuration (for future testing)
