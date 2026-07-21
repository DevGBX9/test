package devgbx9.mineflayer.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.Random;

public class FakeBot {
    private final String name;
    private final UUID uuid;
    private final OtherClientPlayerEntity entity;

    private boolean standStill = false;
    private boolean lookAtEnabled = true;
    private boolean wanderEnabled = false;

    // Movement tracking
    private Vec3 velocity = Vec3.ZERO;

    // Wander variables
    private Vec3 wanderTarget = null;
    private int wanderCooldown = 0;
    private int wanderTimeout = 0;
    private final Random random = new Random();

    public FakeBot(String name, ClientWorld world, Vec3 pos) {
        this.name = name;
        this.uuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(this.uuid, name);
        this.entity = new OtherClientPlayerEntity(world, profile);
        this.entity.setPosition(pos);
        world.addEntity(this.entity);
    }

    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public OtherClientPlayerEntity getEntity() { return entity; }

    public void remove(ClientWorld world) {
        world.removeEntity(this.entity.getId(), Entity.RemovalReason.DISCARDED);
    }

    public void setStandStill(boolean val) {
        this.standStill = val;
        if (val) {
            this.velocity = Vec3.ZERO;
        }
    }

    public void setWanderEnabled(boolean val) {
        this.wanderEnabled = val;
        if (!val) {
            this.wanderTarget = null;
            this.wanderCooldown = 0;
            this.wanderTimeout = 0;
        }
    }

    public void tick(ClientWorld world) {
        if (entity == null || !entity.isAlive()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        net.minecraft.client.network.ClientPlayerEntity localPlayer = client.player;

        // Apply behaviors
        if (!standStill) {
            // Apply gravity
            double vy = velocity.y;
            boolean onGround = entity.onGround();
            if (!onGround) {
                vy -= 0.08;
                if (vy < -3.92) vy = -3.92;
            } else {
                if (vy < 0) vy = 0;
            }

            // Behaviors
            if (localPlayer != null && localPlayer.isAlive()) {
                double dist = entity.distanceTo(localPlayer);
                if (wanderEnabled) {
                    tickWander(world, vy);
                } else {
                    // Follow local player
                    tickFollow(localPlayer, dist, vy);
                }
            } else {
                // Just apply gravity and decelerate horizontally
                velocity = new Vec3(velocity.x * 0.98, vy, velocity.z * 0.98);
                entity.setVelocity(velocity);
                entity.move(net.minecraft.world.entity.MoverType.SELF, velocity);
            }

            // Sync rotation and pitch lookAt
            tickLook(world, localPlayer);
        } else {
            entity.setVelocity(Vec3.ZERO);
        }
    }

    private void tickFollow(net.minecraft.client.network.ClientPlayerEntity localPlayer, double dist, double vy) {
        if (dist < 2.0) {
            velocity = new Vec3(0, vy, 0);
            entity.setVelocity(velocity);
            entity.move(net.minecraft.world.entity.MoverType.SELF, velocity);
            return;
        }

        double dx = localPlayer.getX() - entity.getX();
        double dz = localPlayer.getZ() - entity.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.001) return;

        double nx = dx / horizontalDist;
        double nz = dz / horizontalDist;
        double speed = dist > 6.0 ? 0.26 : 0.19;

        double vx = nx * speed;
        double vz = nz * speed;

        // Climb and swim checks
        vy = checkClimbAndSwim(entity.getWorld(), nx, nz, vy);

        velocity = new Vec3(vx, vy, vz);
        entity.setVelocity(velocity);
        entity.move(net.minecraft.world.entity.MoverType.SELF, velocity);
    }

    private void tickWander(ClientWorld world, double vy) {
        if (wanderCooldown > 0) {
            wanderCooldown--;
            velocity = new Vec3(0, vy, 0);
            entity.setVelocity(velocity);
            entity.move(net.minecraft.world.entity.MoverType.SELF, velocity);
            return;
        }

        // 1. Detect monsters
        LivingEntity nearestMonster = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Entity e : world.getEntities()) {
            if (e instanceof LivingEntity living && e.isAlive() && e != entity) {
                if (living instanceof Monster || living instanceof net.minecraft.world.entity.monster.Slime || living instanceof net.minecraft.world.entity.monster.Phantom) {
                    double d = e.squaredDistanceTo(entity);
                    if (d < 100.0 && d < nearestDistSq) {
                        nearestDistSq = d;
                        nearestMonster = living;
                    }
                }
            }
        }

        boolean panic = nearestMonster != null;

        if (panic) {
            Vec3 escapeDir = entity.getPos().subtract(nearestMonster.getPos()).multiply(1, 0, 1);
            if (escapeDir.lengthSquared() < 0.001) {
                escapeDir = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            }
            Vec3 escapeTarget = findSafeEscapeTarget(world, escapeDir.normalize());
            if (escapeTarget != null) {
                wanderTarget = escapeTarget;
                wanderCooldown = 0;
                wanderTimeout = 100;
            }
        }

        if (wanderTarget == null) {
            wanderTarget = findWanderTarget(world);
            wanderTimeout = 200;
            if (wanderTarget == null) {
                wanderCooldown = 40 + random.nextInt(60);
                return;
            }
        }

        wanderTimeout--;
        if (wanderTimeout <= 0) {
            wanderTarget = null;
            wanderCooldown = 40 + random.nextInt(80);
            return;
        }

        double distance = entity.getPos().distanceTo(wanderTarget);
        if (distance < 1.5) {
            wanderTarget = null;
            wanderCooldown = panic ? 10 + random.nextInt(20) : 40 + random.nextInt(80);
            velocity = new Vec3(0, vy, 0);
            entity.setVelocity(velocity);
            entity.move(net.minecraft.world.entity.MoverType.SELF, velocity);
            return;
        }

        double dx = wanderTarget.x - entity.getX();
        double dz = wanderTarget.z - entity.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.001) return;

        double nx = dx / horizontalDist;
        double nz = dz / horizontalDist;
        double speed = panic ? 0.26 : 0.14;

        double vx = nx * speed;
        double vz = nz * speed;

        vy = checkClimbAndSwim(world, nx, nz, vy);

        velocity = new Vec3(vx, vy, vz);
        entity.setVelocity(velocity);
        entity.move(net.minecraft.world.entity.MoverType.SELF, velocity);
        entity.setSprinting(panic);
    }

    private double checkClimbAndSwim(net.minecraft.world.level.Level world, double nx, double nz, double vy) {
        boolean onGround = entity.onGround();
        BlockPos feetPos = entity.getBlockPos();

        // Anti-fall check
        BlockPos nextFeetPos = BlockPos.ofFloored(entity.getX() + nx * 0.6, entity.getY(), entity.getZ() + nz * 0.6);
        boolean isSafe = false;
        for (int yOffset = 0; yOffset >= -3; yOffset--) {
            BlockPos checkPos = nextFeetPos.down(-yOffset);
            BlockState bs = world.getBlockState(checkPos);
            if (!bs.getCollisionShape(world, checkPos).isEmpty() || !bs.getFluidState().isEmpty()) {
                isSafe = true;
                break;
            }
        }

        if (!isSafe && wanderEnabled) {
            // Cancel wander target if unsafe
            wanderTarget = null;
            wanderCooldown = 20 + random.nextInt(40);
            return vy;
        }

        // Jump over 1-block obstacles
        if (onGround) {
            BlockState feetFront = world.getBlockState(nextFeetPos);
            BlockState headFront = world.getBlockState(nextFeetPos.up());
            if (!feetFront.getCollisionShape(world, nextFeetPos).isEmpty() && headFront.getCollisionShape(world, nextFeetPos.up()).isEmpty()) {
                vy = 0.42;
            }
        }

        // Swim up in water/lava
        BlockState currentBS = world.getBlockState(feetPos);
        if (!currentBS.getFluidState().isEmpty()) {
            vy = 0.15;
        }

        return vy;
    }

    private void tickLook(ClientWorld world, LivingEntity localPlayer) {
        if (localPlayer != null && localPlayer.isAlive() && !wanderEnabled) {
            if (lookAtEnabled) {
                lookAt(localPlayer.getEyePos());
            }
        } else if (wanderEnabled && wanderTarget != null) {
            lookAt(wanderTarget.add(0, 0.5, 0));
        }
    }

    private void lookAt(Vec3 target) {
        Vec3 eyePos = entity.getEyePos();
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.001 || Math.abs(dy) > 0.001) {
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            if (pitch > 90) pitch = 90;
            if (pitch < -90) pitch = -90;
            entity.setYaw(yaw);
            entity.setPitch(pitch);
            entity.setHeadYaw(yaw);
        }
    }

    private Vec3 findWanderTarget(ClientWorld world) {
        Vec3 pos = entity.getPos();
        for (int attempts = 0; attempts < 10; attempts++) {
            double offsetX = (random.nextDouble() * 16.0) - 8.0;
            double offsetZ = (random.nextDouble() * 16.0) - 8.0;

            double targetX = pos.x + offsetX;
            double targetZ = pos.z + offsetZ;

            BlockPos check = BlockPos.ofFloored(targetX, pos.y + 3, targetZ);
            BlockPos ground = null;
            for (int y = 0; y < 10; y++) {
                BlockPos p = check.down(y);
                if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) {
                    ground = p;
                    break;
                }
            }

            if (ground == null) continue;

            BlockPos targetPos = ground.up();
            if (!world.getBlockState(targetPos).getCollisionShape(world, targetPos).isEmpty() ||
                !world.getBlockState(targetPos.up()).getCollisionShape(world, targetPos.up()).isEmpty()) {
                continue;
            }

            double heightDiff = Math.abs(targetPos.getY() - pos.y);
            if (heightDiff > 4.0) continue;

            return new Vec3(targetX, targetPos.getY(), targetZ);
        }
        return null;
    }

    private Vec3 findSafeEscapeTarget(ClientWorld world, Vec3 direction) {
        Vec3 pos = entity.getPos();
        double[] angles = {0, 30, -30, 60, -60, 90, -90};
        for (double angle : angles) {
            Vec3 rotDir = rotateVector(direction, angle);
            double targetX = pos.x + rotDir.x * 8.0;
            double targetZ = pos.z + rotDir.z * 8.0;

            BlockPos check = BlockPos.ofFloored(targetX, pos.y + 3, targetZ);
            BlockPos ground = null;
            for (int y = 0; y < 10; y++) {
                BlockPos p = check.down(y);
                if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) {
                    ground = p;
                    break;
                }
            }

            if (ground == null) continue;

            BlockPos targetPos = ground.up();
            if (!world.getBlockState(targetPos).getCollisionShape(world, targetPos).isEmpty() ||
                !world.getBlockState(targetPos.up()).getCollisionShape(world, targetPos.up()).isEmpty()) {
                continue;
            }

            // Check if next step is safe (anti-fall)
            BlockPos nextFeetPos = BlockPos.ofFloored(pos.x + rotDir.x * 0.6, pos.y, pos.z + rotDir.z * 0.6);
            boolean stepSafe = false;
            for (int yOffset = 0; yOffset >= -3; yOffset--) {
                BlockPos checkPos = nextFeetPos.down(-yOffset);
                BlockState bs = world.getBlockState(checkPos);
                if (!bs.getCollisionShape(world, checkPos).isEmpty() || !bs.getFluidState().isEmpty()) {
                    stepSafe = true;
                    break;
                }
            }
            if (!stepSafe) continue;

            return new Vec3(targetX, targetPos.getY(), targetZ);
        }
        return null;
    }

    private Vec3 rotateVector(Vec3 vector, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = vector.x * cos - vector.z * sin;
        double z = vector.x * sin + vector.z * cos;
        return new Vec3(x, 0, z);
    }
}
