package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class BotNPC {

    private final String name;
    private final UUID uuid;
    private Object serverPlayer;
    private Player bukkitPlayer;
    private boolean alive;

    // Cached reflection handles for tick
    private Method tickMethod;
    private Method doTickMethod;
    private Field connectionField;

    // Keepalive fields — cached once
    private Object cachedListener;
    private Object cachedRawConn;
    private Field keepAliveTimeField;
    private Field tickCountField;
    private Field lastReceivedTimeField;
    private boolean keepAliveCached = false;

    // Fall damage tracking
    private double highestY = -1;

    // Default look direction tracking
    private float spawnYaw;

    // Toggle flags
    private boolean standStill = false;
    private boolean lookAtEnabled = true;
    private boolean respawnEnabled = false;
    private Location spawnLocation;

    // Follow system
    private Player followTarget = null;

    // Wander system
    private boolean wanderEnabled = false;
    private Location wanderTarget = null;
    private int wanderCooldown = 0;
    private int wanderTimeout = 0;
    private static final java.util.Random RANDOM = new java.util.Random();

    public BotNPC(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public boolean isAlive() { return alive; }
    public Player getBukkitPlayer() { return bukkitPlayer; }
    public Object getServerPlayer() { return serverPlayer; }

    public boolean isStandStill() { return standStill; }
    public void setStandStill(boolean standStill) {
        this.standStill = standStill;
        if (bukkitPlayer != null) {
            bukkitPlayer.setGravity(!standStill);
            if (standStill) {
                bukkitPlayer.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            }
        }
    }
    public boolean isLookAtEnabled() { return lookAtEnabled; }
    public void setLookAtEnabled(boolean lookAtEnabled) { this.lookAtEnabled = lookAtEnabled; }
    public boolean isRespawnEnabled() { return respawnEnabled; }
    public void setRespawnEnabled(boolean respawnEnabled) { this.respawnEnabled = respawnEnabled; }
    public Location getSpawnLocation() { return spawnLocation; }

    public boolean isWanderEnabled() { return wanderEnabled; }
    public void setWanderEnabled(boolean wanderEnabled) {
        this.wanderEnabled = wanderEnabled;
        if (!wanderEnabled) {
            wanderTarget = null;
            wanderCooldown = 0;
            wanderTimeout = 0;
        }
    }

    public Player getFollowTarget() { return followTarget; }
    public void setFollowTarget(Player target) { this.followTarget = target; }
    public void clearFollowTarget() { this.followTarget = null; }

    public void spawn(Location location, Object profile) {
        if (alive) return;
        if (!NMSHelper.isAvailable()) {
            Bukkit.getLogger().severe("[Mineflayer] NMS unavailable");
            return;
        }
        try {
            serverPlayer = NMSHelper.createAndJoinFakePlayer(name, location, profile);
            bukkitPlayer = NMSHelper.toBukkitPlayer(serverPlayer);
            bukkitPlayer.teleport(location);

            // Force gravity ON and gamemode to SURVIVAL for proper physics
            bukkitPlayer.setGravity(true);
            bukkitPlayer.setGameMode(org.bukkit.GameMode.SURVIVAL);
            this.spawnYaw = location.getYaw();
            this.spawnLocation = location.clone();

            NMSHelper.registerEntityInWorld(serverPlayer, location.getWorld());
            NMSHelper.broadcastBotSpawn(serverPlayer);

            // Cache tick method from ServerPlayer or its superclasses
            for (Class<?> cls = serverPlayer.getClass(); cls != null; cls = cls.getSuperclass()) {
                try {
                    tickMethod = cls.getDeclaredMethod("doTick");
                    tickMethod.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (tickMethod == null) {
                for (Class<?> cls = serverPlayer.getClass(); cls != null; cls = cls.getSuperclass()) {
                    try {
                        tickMethod = cls.getDeclaredMethod("tick");
                        tickMethod.setAccessible(true);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
            }

            // Cache connection field
            connectionField = null;
            try {
                Class<?> listenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
                for (Field f : serverPlayer.getClass().getDeclaredFields()) {
                    if (f.getType().isAssignableFrom(listenerCls)) {
                        f.setAccessible(true);
                        connectionField = f;
                        break;
                    }
                }
                // Also check superclasses
                if (connectionField == null) {
                    for (Class<?> cls = serverPlayer.getClass(); cls != null; cls = cls.getSuperclass()) {
                        for (Field f : cls.getDeclaredFields()) {
                            if (listenerCls.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                connectionField = f;
                                break;
                            }
                        }
                        if (connectionField != null) break;
                    }
                }
            } catch (Exception ignored) {}

            Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' spawned (tickMethod=" + (tickMethod != null ? tickMethod.getName() : "null") + ", connectionField=" + (connectionField != null) + ")");
            alive = true;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Mineflayer] spawn failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void remove() {
        if (!alive) return;
        if (bukkitPlayer != null) {
            try { bukkitPlayer.kickPlayer("Bot removed"); } catch (Exception ignored) {}
        }
        if (serverPlayer != null) {
            try { NMSHelper.removeFakePlayer(serverPlayer); } catch (Exception ignored) {}
        }
        broadcastLeave(name);
        serverPlayer = null;
        bukkitPlayer = null;
        alive = false;
    }

    public void tick() {
        if (!alive || serverPlayer == null || bukkitPlayer == null) return;

        try {
            // === 1. KEEPALIVE: Prevent timeout disconnect ===
            ensureKeepalive();

            // === 1.5. FOLLOW / WANDER MOVEMENT ===
            if (!standStill && followTarget != null) {
                tickFollow();
            } else if (!standStill && wanderEnabled && followTarget == null) {
                tickWander();
            }

            // === 2. NATIVE TICK: Call ServerPlayer.doTick() or tick() for full physics ===
            if (!standStill) {
                if (tickMethod != null) {
                    try {
                        tickMethod.invoke(serverPlayer);
                    } catch (Exception e) {
                        // Swallow NPEs from connection-related code inside tick()
                    }
                } else {
                    manualPhysicsFallback();
                }
            }

            // === Fall Damage Logic ===
            if (!standStill) {
                double currentY = bukkitPlayer.getLocation().getY();
                org.bukkit.block.Block currentBlock = bukkitPlayer.getLocation().getBlock();
                boolean isLiquidOrCobweb = currentBlock.isLiquid() || currentBlock.getType() == org.bukkit.Material.COBWEB;

                if (bukkitPlayer.isOnGround()) {
                    if (highestY != -1 && !isLiquidOrCobweb) {
                        double fallDistance = highestY - currentY;
                        if (fallDistance > 3.0) {
                            double damage = fallDistance - 3.0;
                            org.bukkit.event.entity.EntityDamageEvent event = new org.bukkit.event.entity.EntityDamageEvent(
                                bukkitPlayer,
                                org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,
                                damage
                            );
                            Bukkit.getPluginManager().callEvent(event);
                            if (!event.isCancelled()) {
                                bukkitPlayer.setLastDamageCause(event);
                                bukkitPlayer.damage(event.getFinalDamage());
                            }
                        }
                    }
                    highestY = -1;
                } else {
                    if (isLiquidOrCobweb) {
                        highestY = -1;
                    } else if (highestY == -1 || currentY > highestY) {
                        highestY = currentY;
                    }
                }
            }

            // === 3. HEAD TRACKING: Look at nearest player/mob, follow target, or wander target ===
            if (bukkitPlayer.isOnline()) {
                if (followTarget != null && followTarget.isOnline()) {
                    // When following, always look at the follow target
                    lookAtEntity(followTarget);
                } else if (wanderEnabled && wanderTarget != null) {
                    // Look at the wander target
                    Location botEyeLoc = bukkitPlayer.getEyeLocation();
                    double dx = wanderTarget.getX() - botEyeLoc.getX();
                    double dy = (wanderTarget.getY() + 0.5) - botEyeLoc.getY();
                    double dz = wanderTarget.getZ() - botEyeLoc.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > 0.001 || Math.abs(dy) > 0.001) {
                        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                        if (pitch > 90) pitch = 90;
                        if (pitch < -90) pitch = -90;
                        NMSHelper.setRotation(serverPlayer, yaw, pitch, yaw);
                    }
                } else if (lookAtEnabled) {
                    Player nearest = findNearestPlayer();
                    boolean looked = false;
                    if (nearest != null) {
                        double distSquared = nearest.getLocation().distanceSquared(bukkitPlayer.getLocation());
                        if (distSquared <= 25.0) {
                            lookAtEntity(nearest);
                            looked = true;
                        }
                    }
                    if (!looked) {
                        org.bukkit.entity.LivingEntity nearestMob = null;
                        double nearestMobDistSq = Double.MAX_VALUE;
                        for (org.bukkit.entity.Entity entity : bukkitPlayer.getNearbyEntities(5.0, 5.0, 5.0)) {
                            if (entity instanceof org.bukkit.entity.LivingEntity living) {
                                if (living instanceof Player) continue;
                                double distSq = living.getLocation().distanceSquared(bukkitPlayer.getLocation());
                                if (distSq < nearestMobDistSq) {
                                    nearestMobDistSq = distSq;
                                    nearestMob = living;
                                }
                            }
                        }
                        if (nearestMob != null && nearestMobDistSq <= 25.0) {
                            lookAtEntity(nearestMob);
                            looked = true;
                        }
                    }
                    if (!looked) {
                        NMSHelper.setRotation(serverPlayer, spawnYaw, 0f, spawnYaw);
                    }
                } else {
                    // lookAt disabled — always face spawn direction
                    NMSHelper.setRotation(serverPlayer, spawnYaw, 0f, spawnYaw);
                }
            }
        } catch (Exception ignored) {}
    }
    /**
     * Look at a specific living entity (player or mob), tracking their head.
     */
    private void lookAtEntity(org.bukkit.entity.LivingEntity target) {
        Location botEyeLoc = bukkitPlayer.getEyeLocation();
        Location targetLoc = target.getEyeLocation();
        double dx = targetLoc.getX() - botEyeLoc.getX();
        double dy = targetLoc.getY() - botEyeLoc.getY();
        double dz = targetLoc.getZ() - botEyeLoc.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.001 || Math.abs(dy) > 0.001) {
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            if (pitch > 90) pitch = 90;
            if (pitch < -90) pitch = -90;
            NMSHelper.setRotation(serverPlayer, yaw, pitch, yaw);
        }
    }

    /**
     * Smart follow movement: calculate velocity toward follow target,
     * handle jumping over obstacles, and sprint when far away.
     */
    private void tickFollow() {
        if (followTarget == null || !followTarget.isOnline()) {
            followTarget = null;
            return;
        }

        Location botLoc = bukkitPlayer.getLocation();
        Location targetLoc = followTarget.getLocation();

        // Don't follow if in different worlds
        if (!botLoc.getWorld().equals(targetLoc.getWorld())) return;

        double distance = botLoc.distance(targetLoc);

        // If close enough, stop moving
        if (distance < 2.0) {
            bukkitPlayer.setVelocity(new Vector(0, bukkitPlayer.getVelocity().getY(), 0));
            return;
        }

        // Calculate direction toward target
        double dx = targetLoc.getX() - botLoc.getX();
        double dz = targetLoc.getZ() - botLoc.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.001) return;

        // Normalize direction
        double nx = dx / horizontalDist;
        double nz = dz / horizontalDist;

        // Speed: sprint when far, walk when closer
        double speed = distance > 6.0 ? 0.26 : 0.19;

        // Calculate horizontal velocity
        double vx = nx * speed;
        double vz = nz * speed;
        double vy = bukkitPlayer.getVelocity().getY();

        boolean onGround = bukkitPlayer.isOnGround();

        // Anti-fall check: check if the next step is a cliff/hole
        Location nextStep = botLoc.clone().add(nx * 0.6, 0, nz * 0.6);
        boolean isSafe = false;
        // Check down to 4 blocks below
        for (int yOffset = 0; yOffset >= -4; yOffset--) {
            Block b = nextStep.clone().add(0, yOffset, 0).getBlock();
            if (b.getType().isSolid() || b.isLiquid()) {
                isSafe = true;
                break;
            }
        }

        // If next step is a dangerous fall, and the player is not down there
        if (!isSafe && targetLoc.getY() >= botLoc.getY() - 1.5) {
            // Stop horizontal movement
            vx = 0;
            vz = 0;
            // Try to jump across a gap if target is close
            if (onGround && distance < 5.0) {
                vy = 0.42;
                vx = nx * speed;
                vz = nz * speed;
            }
        }

        // Jump detection: check if there's a solid block in front at feet level
        if (onGround) {
            Location feetFront = botLoc.clone().add(nx * 0.6, 0, nz * 0.6);
            Block blockAtFeet = feetFront.getBlock();
            Block blockAboveFeet = feetFront.clone().add(0, 1.0, 0).getBlock();

            if (blockAtFeet.getType().isSolid() && !blockAboveFeet.getType().isSolid()) {
                vy = 0.42;
            }
        }

        // Handle water/lava: swim up
        Block currentBlock = botLoc.getBlock();
        if (currentBlock.isLiquid()) {
            vy = 0.15;
        }

        // Set sprint state
        bukkitPlayer.setSprinting(distance > 6.0);

        // Apply velocity
        bukkitPlayer.setVelocity(new Vector(vx, vy, vz));

        // Face the direction of movement (yaw) — head tracking will look at target separately
        float moveYaw = (float) Math.toDegrees(Math.atan2(nz, nx)) - 90;
        NMSHelper.setRotation(serverPlayer, moveYaw, 0f, moveYaw);
    }

    /**
     * Wander like a passive Minecraft mob: pick random nearby spots,
     * walk to them, pause, and repeat. Runs away from nearby hostile monsters.
     */
    private void tickWander() {
        Location botLoc = bukkitPlayer.getLocation();

        // 1. Check for nearby monsters to trigger panic mode
        org.bukkit.entity.LivingEntity nearestMonster = null;
        double nearestMonsterDistSq = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity entity : bukkitPlayer.getNearbyEntities(10.0, 10.0, 10.0)) {
            if (entity instanceof org.bukkit.entity.LivingEntity living) {
                if (living instanceof org.bukkit.entity.Monster || 
                    living instanceof org.bukkit.entity.Phantom || 
                    living instanceof org.bukkit.entity.Slime) {
                    
                    double distSq = living.getLocation().distanceSquared(botLoc);
                    if (distSq < nearestMonsterDistSq) {
                        nearestMonsterDistSq = distSq;
                        nearestMonster = living;
                    }
                }
            }
        }

        boolean panic = nearestMonster != null;

        if (panic) {
            // Panic behavior: calculate escape direction (away from monster)
            Vector escapeDir = botLoc.toVector().subtract(nearestMonster.getLocation().toVector()).setY(0);
            if (escapeDir.lengthSquared() < 0.001) {
                escapeDir = new Vector(RANDOM.nextDouble() - 0.5, 0, RANDOM.nextDouble() - 0.5);
            }
            Location escapeTarget = findSafeEscapeTarget(botLoc, escapeDir.normalize());
            if (escapeTarget != null) {
                wanderTarget = escapeTarget;
                wanderCooldown = 0; // Cancel cooldown to run immediately
                wanderTimeout = 100; // 5 seconds max to escape to this spot
            }
        }

        // Cooldown: bot is pausing between walks (like passive mobs idle) — skipped during panic
        if (!panic && wanderCooldown > 0) {
            wanderCooldown--;
            return;
        }

        // Pick a new wander target if we don't have one and not in panic
        if (wanderTarget == null) {
            wanderTarget = findWanderTarget(botLoc);
            wanderTimeout = 200; // 10 seconds max to reach target
            if (wanderTarget == null) {
                // No valid target found, wait a bit
                wanderCooldown = 40 + RANDOM.nextInt(60); // 2-5 seconds
                return;
            }
        }

        // Timeout: give up and pick a new target after a while
        wanderTimeout--;
        if (wanderTimeout <= 0) {
            wanderTarget = null;
            wanderCooldown = 40 + RANDOM.nextInt(80); // 2-6 seconds pause
            return;
        }

        // Check if we've arrived
        double distance = botLoc.distance(wanderTarget);
        if (distance < 1.5) {
            wanderTarget = null;
            if (panic) {
                // If we just finished escaping, pause briefly to see if still threatened
                wanderCooldown = 10 + RANDOM.nextInt(20);
            } else {
                wanderCooldown = 40 + RANDOM.nextInt(80); // 2-6 seconds pause
            }
            bukkitPlayer.setVelocity(new Vector(0, bukkitPlayer.getVelocity().getY(), 0));
            return;
        }

        // Walk/Run toward target
        double dx = wanderTarget.getX() - botLoc.getX();
        double dz = wanderTarget.getZ() - botLoc.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.001) return;

        double nx = dx / horizontalDist;
        double nz = dz / horizontalDist;
        
        // Speed: run fast if in panic, walk slowly otherwise
        double speed = panic ? 0.26 : 0.14;

        double vx = nx * speed;
        double vz = nz * speed;
        double vy = bukkitPlayer.getVelocity().getY();

        boolean onGround = bukkitPlayer.isOnGround();

        // Anti-fall check
        Location nextStep = botLoc.clone().add(nx * 0.6, 0, nz * 0.6);
        boolean isSafe = false;
        for (int yOffset = 0; yOffset >= -3; yOffset--) {
            Block b = nextStep.clone().add(0, yOffset, 0).getBlock();
            if (b.getType().isSolid() || b.isLiquid()) {
                isSafe = true;
                break;
            }
        }
        if (!isSafe) {
            // Dangerous drop ahead, pick a new target
            wanderTarget = null;
            wanderCooldown = 20 + RANDOM.nextInt(40);
            return;
        }

        // Jump over 1-block obstacles
        if (onGround) {
            Location feetFront = botLoc.clone().add(nx * 0.6, 0, nz * 0.6);
            Block blockAtFeet = feetFront.getBlock();
            Block blockAboveFeet = feetFront.clone().add(0, 1.0, 0).getBlock();
            if (blockAtFeet.getType().isSolid() && !blockAboveFeet.getType().isSolid()) {
                vy = 0.42;
            }
        }

        // Swim up if in liquid
        Block currentBlock = botLoc.getBlock();
        if (currentBlock.isLiquid()) {
            vy = 0.15;
        }

        bukkitPlayer.setVelocity(new Vector(vx, vy, vz));

        // Face direction of movement
        float moveYaw = (float) Math.toDegrees(Math.atan2(nz, nx)) - 90;
        NMSHelper.setRotation(serverPlayer, moveYaw, 0f, moveYaw);

        // Set sprinting animation if in panic
        bukkitPlayer.setSprinting(panic);
    }

    /**
     * Find a valid random wander target nearby, like Minecraft's RandomStrollGoal.
     */
    private Location findWanderTarget(Location origin) {
        for (int attempts = 0; attempts < 10; attempts++) {
            double offsetX = (RANDOM.nextDouble() * 16.0) - 8.0; // -8 to +8 blocks
            double offsetZ = (RANDOM.nextDouble() * 16.0) - 8.0;

            double targetX = origin.getX() + offsetX;
            double targetZ = origin.getZ() + offsetZ;

            // Find ground level at target X/Z
            Location check = new Location(origin.getWorld(), targetX, origin.getY() + 3, targetZ);
            Block ground = null;
            for (int y = 0; y < 10; y++) {
                Block b = check.clone().add(0, -y, 0).getBlock();
                if (b.getType().isSolid()) {
                    ground = b;
                    break;
                }
            }

            if (ground == null) continue; // No ground found, skip

            Location target = ground.getLocation().add(0.5, 1.0, 0.5);

            // Check if there's 2 blocks of air above (player can fit)
            Block above1 = target.getBlock();
            Block above2 = target.clone().add(0, 1, 0).getBlock();
            if (above1.getType().isSolid() || above2.getType().isSolid()) continue;

            // Check height difference is not too extreme
            double heightDiff = Math.abs(target.getY() - origin.getY());
            if (heightDiff > 4.0) continue;

            return target;
        }
        return null;
    }

    /**
     * Find a safe escape target away from a monster.
     * Projects a target block away and scans adjacent angles (up to 90 deg) if blocked or cliff.
     */
    private Location findSafeEscapeTarget(Location origin, Vector direction) {
        double[] angles = {0, 30, -30, 60, -60, 90, -90};
        for (double angle : angles) {
            Vector rotDir = rotateVector(direction, angle);
            double targetX = origin.getX() + rotDir.getX() * 8.0;
            double targetZ = origin.getZ() + rotDir.getZ() * 8.0;

            Location check = new Location(origin.getWorld(), targetX, origin.getY() + 3, targetZ);
            Block ground = null;
            for (int y = 0; y < 10; y++) {
                Block b = check.clone().add(0, -y, 0).getBlock();
                if (b.getType().isSolid()) {
                    ground = b;
                    break;
                }
            }

            if (ground == null) continue;

            Location target = ground.getLocation().add(0.5, 1.0, 0.5);
            Block above1 = target.getBlock();
            Block above2 = target.clone().add(0, 1, 0).getBlock();
            if (above1.getType().isSolid() || above2.getType().isSolid()) continue;

            // Make sure next step is safe (anti-fall check)
            double nx = rotDir.getX();
            double nz = rotDir.getZ();
            Location nextStep = origin.clone().add(nx * 0.6, 0, nz * 0.6);
            boolean stepSafe = false;
            for (int yOffset = 0; yOffset >= -3; yOffset--) {
                Block b = nextStep.clone().add(0, yOffset, 0).getBlock();
                if (b.getType().isSolid() || b.isLiquid()) {
                    stepSafe = true;
                    break;
                }
            }
            if (!stepSafe) continue;

            return target;
        }
        return null;
    }

    /**
     * Helper to rotate a 2D vector on the XZ plane.
     */
    private Vector rotateVector(Vector vector, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, 0, z);
    }

    /**
     * Fallback manual gravity simulation if ServerPlayer.tick() is unavailable.
     */
    private void manualPhysicsFallback() {
        try {
            org.bukkit.util.Vector velocity = bukkitPlayer.getVelocity();
            double vx = velocity.getX();
            double vy = velocity.getY();
            double vz = velocity.getZ();

            boolean onGround = bukkitPlayer.isOnGround();
            boolean hasGravity = bukkitPlayer.hasGravity();

            if (hasGravity && !onGround) {
                vy -= 0.08;
                if (vy < -3.92) vy = -3.92;
            }

            double friction = onGround ? 0.546 : 0.98;
            vx *= friction;
            vz *= friction;

            Object vec3Move = NMSHelper.createVec3(vx, vy, vz);
            if (vec3Move != null) {
                NMSHelper.move(serverPlayer, vec3Move);
            }

            org.bukkit.util.Vector postVel = bukkitPlayer.getVelocity();
            double pvx = postVel.getX();
            double pvy = postVel.getY();
            double pvz = postVel.getZ();
            if (bukkitPlayer.isOnGround() && pvy < 0) {
                pvy = 0;
            }
            bukkitPlayer.setVelocity(new org.bukkit.util.Vector(pvx, pvy, pvz));
        } catch (Exception ignored) {}
    }

    /**
     * Prevents the server from kicking the bot for timeout.
     * Resets keepalive timers on the connection and listener.
     */
    private void ensureKeepalive() {
        try {
            if (!keepAliveCached && connectionField != null) {
                keepAliveCached = true;
                cachedListener = connectionField.get(serverPlayer);
                if (cachedListener != null) {
                    // Find raw Connection object inside the listener
                    for (Class<?> cls = cachedListener.getClass(); cls != null; cls = cls.getSuperclass()) {
                        for (Field f : cls.getDeclaredFields()) {
                            if (f.getType().getName().contains("Connection")) {
                                f.setAccessible(true);
                                cachedRawConn = f.get(cachedListener);
                                break;
                            }
                        }
                        if (cachedRawConn != null) break;
                    }

                    // Cache keepalive fields on the listener
                    for (Class<?> cls = cachedListener.getClass(); cls != null; cls = cls.getSuperclass()) {
                        for (Field f : cls.getDeclaredFields()) {
                            f.setAccessible(true);
                            String fn = f.getName();
                            if (f.getType() == long.class) {
                                if (fn.contains("keepAlive") || fn.contains("KeepAlive") || fn.contains("lastKeepAlive")) {
                                    keepAliveTimeField = f;
                                }
                            }
                        }
                    }

                    // Cache fields on raw connection
                    if (cachedRawConn != null) {
                        for (Class<?> cls = cachedRawConn.getClass(); cls != null; cls = cls.getSuperclass()) {
                            for (Field f : cls.getDeclaredFields()) {
                                f.setAccessible(true);
                                String fn = f.getName();
                                if (f.getType() == long.class && (fn.contains("lastReceived") || fn.contains("LastReceived"))) {
                                    lastReceivedTimeField = f;
                                }
                                if (f.getType() == int.class && fn.equals("tickCount")) {
                                    tickCountField = f;
                                }
                            }
                        }
                    }
                }
            }

            // Apply keepalive resets every tick
            long now = System.currentTimeMillis();
            if (keepAliveTimeField != null && cachedListener != null) {
                try { keepAliveTimeField.setLong(cachedListener, now); } catch (Exception ignored) {}
            }
            if (lastReceivedTimeField != null && cachedRawConn != null) {
                try { lastReceivedTimeField.setLong(cachedRawConn, now); } catch (Exception ignored) {}
            }
            if (tickCountField != null && cachedRawConn != null) {
                try { tickCountField.setInt(cachedRawConn, 0); } catch (Exception ignored) {}
            }

            // Also reset keepalive fields by scanning ALL long fields on the listener
            // (handles obfuscated field names)
            if (cachedListener != null) {
                for (Class<?> cls = cachedListener.getClass(); cls != null; cls = cls.getSuperclass()) {
                    for (Field f : cls.getDeclaredFields()) {
                        if (f.getType() == long.class) {
                            try {
                                f.setAccessible(true);
                                long val = f.getLong(cachedListener);
                                // If it looks like a timestamp (within ~5min of now), reset it
                                if (val > now - 600_000 && val < now + 60_000 && val != now) {
                                    f.setLong(cachedListener, now);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private Player findNearestPlayer() {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(bukkitPlayer)) continue;
            if (!p.getWorld().equals(bukkitPlayer.getWorld())) continue;
            double dist = p.getLocation().distanceSquared(bukkitPlayer.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private void broadcastLeave(String name) {
        Bukkit.broadcastMessage("§e" + name + " left the game");
    }
}
