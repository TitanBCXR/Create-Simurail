# Static Production Verification Report

**Generated**: 2026-10-08 04:10 UTC  
**Commit**: 9d45a93  
**Jar**: `/opt/cursor/artifacts/simurail-0.1.0+mc1.21.1-fixed.jar`  
**MD5**: `e90ce3ba5dbaf219b9e964c1164f529c`

---

## Method: Static Analysis with Mojang-Mapped Classes

Using Loom's cached Mojang-mapped Minecraft classes at:
```
/home/ubuntu/.gradle/caches/fabric-loom/1.21.1/neoforge/21.1.249/minecraft-merged-mojang.jar
```

This jar contains the official Mojang mappings used in production environments (NeoForge + official mappings).

Tool: `javap -s` (shows method signatures/descriptors as they appear in bytecode)

---

## ItemEntityPhysicsMixin Production Verification

**File**: `src/main/java/com/crystaelix/simurail/mixin/ItemEntityPhysicsMixin.java`  
**Target Class**: `net.minecraft.world.entity.item.ItemEntity`

### Mixin Annotations Analysis

#### 1. @Inject(method = "tick", at = @At("HEAD"))

**Target Method**: `tick()`  
**Expected Descriptor**: `()V`

**Verification**:
```bash
$ javap -s ItemEntity | grep -A1 "public void tick()"
  public void tick();
    descriptor: ()V
```

✅ **PASS**: Method `tick()` is **DECLARED** on `ItemEntity` (not inherited)  
✅ **PASS**: Descriptor matches `()V`  
✅ **PASS**: Production-safe without refmap

---

### Methods Called Within Injected Code

These methods are **called** from within the mixin, not injection targets. They can be inherited.

#### Called Methods from ItemEntity:

1. **`getItem()`**
   ```
   public net.minecraft.world.item.ItemStack getItem();
     descriptor: ()Lnet/minecraft/world/item/ItemStack;
   ```
   ✅ **DECLARED on ItemEntity** - Line 48: `return 0.25 * self.getItem().getCount();`

2. **`getAge()`**
   ```
   public int getAge();
     descriptor: ()I
   ```
   ✅ **DECLARED on ItemEntity** - Line 59: `self.getAge() > 10`

#### Called Methods from Entity (inherited - OK for calls):

3. **`isRemoved()`**
   ```
   // From Entity.class
   public final boolean isRemoved();
   ```
   ✅ **INHERITED** - But only called, not injected - Line 55: `!self.isRemoved()`

4. **`level()`**
   ```
   // From Entity.class  
   public net.minecraft.world.level.Level level();
   ```
   ✅ **INHERITED** - But only called, not injected - Lines 56, 66, 72: `self.level()`

5. **`position()`** (from Entity)
   ✅ **INHERITED** - But only called - Line 91: `self.position()`

6. **`getDeltaMovement()` / `setDeltaMovement()`** (from Entity)
   ✅ **INHERITED** - But only called - Lines 99, 102

7. **`getUUID()`** (from Entity)
   ✅ **INHERITED** - But only called - Line 92

---

### Unique Fields (@Unique)

No production issues with @Unique fields - they're added by the mixin itself.

```java
@Unique private LightweightPhysicsBody simurail$physicsBody;
@Unique private int simurail$physicsTick = 0;
```

✅ **PASS**: @Unique fields are safe in production

---

## Other Mixins in This PR

### PhysicsBogeyBlockEntity (Modified)

**Changes**: Added two lines registering/unregistering sublevels  
**No mixin annotations added/modified** - Pure Java code changes  
✅ **PASS**: No production risk

---

## Mixin Configuration Verification

**File**: `simurail.mixins.json`

### Critical Settings:

```json
{
  "required": true,
  "compatibilityLevel": "JAVA_21",
  "mixinextras": {
    "minVersion": "0.5.0"
  },
  "plugin": "com.crystaelix.simurail.mixin.SimurailMixinPlugin"
}
```

#### Analysis:

1. **"required": true**
   ✅ **PASS**: Mixin application failure will crash (expected for required mixin)

2. **"compatibilityLevel": "JAVA_21"**
   ✅ **PASS**: Matches Minecraft 1.21.1 Java requirement

3. **No "refmap" specified**
   ✅ **PASS**: Correct for production - mixins must resolve without refmap
   - Production behavior: Mixin system will look for methods by name+descriptor
   - Our injection targets methods **declared** on target class, not inherited
   - Therefore works without refmap

4. **"plugin": "com.crystaelix.simurail.mixin.SimurailMixinPlugin"**
   ✅ **PASS**: Plugin exists and doesn't affect method resolution

---

## Comparison: Before vs After Fix

### BEFORE (Broken in Production):

```java
@Inject(method = "remove", at = @At("HEAD"))  // ❌ FAIL
private void simurail$onRemove(CallbackInfo ci) { ... }
```

**Failure reason**: `remove()` is inherited from `Entity`, not declared on `ItemEntity`  
**Error**: `InvalidInjectionException: could not find any targets matching 'remove'`

### AFTER (Fixed):

```java
@Inject(method = "tick", at = @At("HEAD"))  // ✅ PASS
private void simurail$onTick(CallbackInfo ci) { 
    if (!simurail$shouldHavePhysics()) {  // Checks isRemoved()
        // Cleanup happens automatically
    }
}
```

**Success reason**: `tick()` is declared on `ItemEntity`, resolvable without refmap

---

## Production Check Summary

### All Injection Targets:

| Mixin | Target Method | Descriptor | Declared On Target? | Status |
|-------|--------------|------------|---------------------|--------|
| ItemEntityPhysicsMixin | `tick()` | `()V` | ✅ ItemEntity | ✅ PASS |

### Removed Injection (Previously Broken):

| Mixin | Target Method | Reason for Removal | Status |
|-------|--------------|-------------------|--------|
| ItemEntityPhysicsMixin | `remove()` | Inherited from Entity | ✅ FIXED |

### Called Methods (Not Injection Targets):

| Method | Source | Usage | Status |
|--------|--------|-------|--------|
| `getItem()` | ItemEntity | Called | ✅ OK |
| `getAge()` | ItemEntity | Called | ✅ OK |
| `isRemoved()` | Entity (inherited) | Called | ✅ OK |
| `level()` | Entity (inherited) | Called | ✅ OK |
| `position()` | Entity (inherited) | Called | ✅ OK |
| `getDeltaMovement()` | Entity (inherited) | Called | ✅ OK |
| `setDeltaMovement()` | Entity (inherited) | Called | ✅ OK |
| `getUUID()` | Entity (inherited) | Called | ✅ OK |

**Note**: Called methods can be inherited - only injection targets must be declared on the target class.

---

## Build Configuration

### Mixin Debug Flags

**Removed from `build.gradle`** to avoid affecting production builds:

```gradle
// REMOVED - these were for dev testing only:
// property "mixin.debug.verify", "true"
// property "mixin.debug.export", "true"
```

These flags are not needed in production and could affect jar behavior or create unnecessary debug output.

---

## Final Verdict

### ✅ ALL CHECKS PASS

1. ✅ **Injection target verified**: `tick()` is declared on `ItemEntity`
2. ✅ **Method descriptor matches**: `()V`  
3. ✅ **No inherited method injections**: Removed problematic `remove()` injection
4. ✅ **Mixin config correct**: No refmap, JAVA_21 compat level
5. ✅ **Called methods OK**: Inherited methods can be called (not injected)
6. ✅ **Build config clean**: Removed dev-only mixin debug flags

### Production Readiness

✅ **READY FOR PRODUCTION**

The jar at `/opt/cursor/artifacts/simurail-0.1.0+mc1.21.1-fixed.jar` should load successfully in:
- NeoForge 21.1.249
- Minecraft 1.21.1
- Official/Mojang mappings (production environment)
- No refmap present

**Expected behavior**: Game loads without mixin errors, items can interact with train physics.

---

## Recommendation

Test the artifact in the same CurseForge environment where the crash occurred:
1. Replace crashed jar with fixed jar
2. Launch game → should reach title screen
3. In-game: test item physics on moving trains
4. Check F3 debug for performance

If successful, this fix can be merged to main and released.
