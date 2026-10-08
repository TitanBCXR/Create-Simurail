# Non-Multiblock Physics Implementation Analysis

## Root Cause Analysis

### Current Architecture
The multiblock requirement stems from **Sable's physics engine architecture**, not Simurail code:

1. **Sable SubLevels**: Sable organizes physics into SubLevels - independent physics containers that act as moving reference frames
   - Each SubLevel is essentially a separate physics world that can move/rotate independently
   - Physics bodies MUST be part of a SubLevel to participate in physics simulation
   - Located in: `dev.ryanhcode.sable.sublevel.ServerSubLevel`

2. **Current Simurail Integration** (see `PhysicsBogeyBlockEntity.java`):
   - Each bogey creates a "pivot" physics body:
     - **SubLevelAuxiliaryPhysicsBody**: Creates a full new sublevel with anchor block (lines 130, 29-30)
     - **BoxAuxiliaryPhysicsBody**: Attaches a box collider to parent sublevel (lines 29, 20)
   - Axles use `AttachableBoxPhysicsObject` for wheel collision (line 132, `PhysicsBogeyAxle.java:20`)
   - Everything connects via joints/constraints to track segments

3. **Why This Works for Trains**:
   - Trains are large, persistent structures
   - One sublevel per car/bogey is acceptable overhead
   - Joints connect them into a physics assembly
   - Justified by complexity (multiple axles, couplers, suspension, etc.)

### The Actual Problem

**Small, loose objects can't efficiently get physics** because:

1. **Creating a full SubLevel per item is too expensive**:
   - Memory: Each sublevel allocates its own physics pipeline, chunk plot, collision structures
   - CPU: Sublevel pose updates, constraint solving, broad-phase collision
   - Overhead justified for trains, wasteful for a single item

2. **BoxPhysicsObject requires a parent sublevel**:
   - `AttachableBoxPhysicsObject` (line 12-30, `AttachableBoxPhysicsObject.java`) requires:
     - A parent `ServerSubLevel` to attach to (line 12)
     - Manual management of attachment lifecycle
   - Items/entities don't naturally belong to a specific sublevel

3. **No activation/pooling system**:
   - All physics bodies are active all the time
   - No proximity-based activation
   - No batching of similar objects

## Sable API Constraints

From examining imports and usage:

### Available Physics Objects (from Sable):
- `BoxPhysicsObject` - simple box collider (used in `AttachableBoxPhysicsObject`)
- Constraints via `GenericConstraintConfiguration` and `FixedConstraintConfiguration`
- `PhysicsPipelineBody` - base interface for physics participation
- `SubLevelPhysicsSystem` - main physics system interface

### Key Sable APIs:
```java
// From PhysicsBogeyAxle.java and related files:
SubLevelPhysicsSystem.require(level).addObject(boxPhysicsObject)
SubLevelPhysicsSystem.require(level).removeObject(boxPhysicsObject)
physics.getPipeline().addConstraint(body1, body2, config)
RigidBodyHandle.of(subLevel).applyImpulseAtPoint(position, force)
Sable.HELPER.getContaining(level, blockPos) // finds sublevel at position
```

### Limitations:
- No native "lightweight entity physics" API in Sable
- No built-in pooling or activation system
- Physics objects must be explicitly added/removed from SubLevelPhysicsSystem
- Cannot have "free-floating" physics without a sublevel parent

## Implementation Strategy

### Approach: Proximity-Based Lightweight Physics Attachment

**Core Idea**: Small objects piggyback on existing sublevels instead of creating their own.

### Components:

#### 1. **LightweightPhysicsManager** (new class)
   - Manages pool of small physics-enabled objects
   - Tracks nearby active sublevels (bogeys, trains)
   - Activates/deactivates physics based on proximity
   - Location: `src/main/java/com/crystaelix/simurail/api/physics/LightweightPhysicsManager.java`

#### 2. **LightweightPhysicsBody** (new class)
   - Wrapper for entities/items that need physics
   - Creates `AttachableBoxPhysicsObject` when near a sublevel
   - Removes physics object when far from all sublevels
   - Handles collision callbacks and physics application
   - Location: `src/main/java/com/crystaelix/simurail/api/physics/LightweightPhysicsBody.java`

#### 3. **Entity/Item Integration**
   - Mixin or interface for entities to opt into lightweight physics
   - Hook into entity tick to update physics body
   - Apply physics results back to entity motion
   - Example: Cargo items on moving trains, derailed bogey parts

#### 4. **Configuration** (add to SimurailPhysicsConfig.java)
   ```java
   public final ConfigGroup lightweight = group(1, "lightweight", "Lightweight Physics");
   public final ConfigBool lightweightEnabled = b(true, "enabled", "Enable lightweight physics for small objects");
   public final ConfigFloat lightweightActivationRadius = f(32, 0, 256, "activationRadius", "Distance to activate physics");
   public final ConfigInt lightweightMaxActive = i(128, 0, 1024, "maxActive", "Max concurrent lightweight physics objects");
   public final ConfigFloat lightweightSleepVelocity = f(0.1, 0, 10, "sleepVelocity", "Velocity threshold for sleeping");
   ```

### Technical Details:

#### Attachment Strategy:
1. **Scan for nearby sublevels**: Use `Sable.HELPER.getContaining()` or scan for bogeys/physics blocks
2. **Choose best sublevel**: Prefer closest, most stable (lowest velocity)
3. **Create BoxPhysicsObject**: Use `AttachableBoxPhysicsObject` with parent sublevel
4. **Update constraints**: If object should follow surface, add constraint joint
5. **Deactivate when far**: Remove physics object, store last state

#### Performance Optimizations:
1. **Spatial indexing**: Only check activation for objects in active chunks
2. **Staggered updates**: Spread activation checks across multiple ticks
3. **Sleep threshold**: Objects below velocity threshold become kinematic
4. **LOD physics**: Reduce collision complexity at distance

#### Entity Physics Application:
```java
// Pseudo-code for entity integration
public void tick() {
    if (lightweightBody != null) {
        lightweightBody.update(this.level, this.position());
        if (lightweightBody.isActive()) {
            Vec3 physicsVel = lightweightBody.getVelocity();
            this.setDeltaMovement(physicsVel);
        }
    }
}
```

## Performance Considerations

### Current Bottlenecks Found:

1. **PhysicsBogeyAxle.java**:
   - Line 320-450: Track finding runs every few ticks, uses raycasts
   - Opportunity: Cache track segments, use spatial hash
   
2. **TrackSegmentFinder.java** (need to review):
   - Likely iterates through all track edges to find nearest
   - Could benefit from spatial indexing

3. **CurvedTrackSegmentCache.java**:
   - Curve computations may be repeated
   - Already has caching, verify effectiveness

4. **No entity collision culling**:
   - SubLevelEntityCollisionMixin adjusts bounds but doesn't cull

### Optimizations to Implement:

1. **Lightweight physics pooling**: Reuse physics objects instead of create/destroy
2. **Deferred activation**: Wait 1-2 ticks before activating physics on dropped items
3. **Batch constraint updates**: Update multiple constraints in one physics step
4. **Spatial hash for sublevels**: O(1) lookup of nearby sublevels instead of scanning

## Testing Strategy

1. **Unit tests**: Physics body lifecycle, attachment/detachment
2. **Integration tests**: Entity interaction with moving trains
3. **Performance tests**: Spawn 1000 items near train, measure frame time
4. **Edge cases**: 
   - Object between two sublevels
   - Sublevel destruction while object attached
   - Chunk unload/reload
   - Items on moving track switches

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Physics desync | Entities jitter or teleport | Add velocity smoothing, position correction |
| Performance regression | Frame drops with many objects | Hard cap on active objects, LOD system |
| Sable API changes | Breaking changes in updates | Document Sable version dependency |
| Collision bugs | Objects fall through floors | Continuous collision detection flag |
| Multiplayer desync | Client/server disagreement | Server authoritative, client prediction |

## Files to Modify/Create

### New Files:
- `src/main/java/com/crystaelix/simurail/api/physics/LightweightPhysicsManager.java`
- `src/main/java/com/crystaelix/simurail/api/physics/LightweightPhysicsBody.java`
- `src/main/java/com/crystaelix/simurail/api/physics/IPhysicsEntity.java` (interface)
- `src/main/java/com/crystaelix/simurail/mixin/ItemEntityPhysicsMixin.java`
- `src/main/java/com/crystaelix/simurail/content/SimurailLightweightPhysics.java` (registration)

### Modified Files:
- `src/main/java/com/crystaelix/simurail/config/SimurailPhysicsConfig.java` - Add lightweight config
- `src/main/java/com/crystaelix/simurail/Simurail.java` - Register manager
- `src/main/resources/simurail.mixins.json` - Register new mixins

### Performance Optimization Targets:
- `src/main/java/com/crystaelix/simurail/content/bogey/PhysicsBogeyAxle.java` - Cache improvements
- `src/main/java/com/crystaelix/simurail/content/track/TrackSegmentHelper.java` - Spatial indexing

## Success Criteria

1. ✅ Dropped items can roll on moving trains without full sublevel
2. ✅ Configuration options control activation and limits
3. ✅ Performance: 100+ items with <5ms frame time impact
4. ✅ `./gradlew build` passes
5. ✅ No crashes with existing bogey/train functionality
6. ✅ Clean code suitable for upstream contribution

## Next Steps

1. Implement LightweightPhysicsManager and LightweightPhysicsBody
2. Add configuration to SimurailPhysicsConfig
3. Create ItemEntity mixin for testing
4. Test with dev server
5. Performance optimization pass
6. Documentation and PR
