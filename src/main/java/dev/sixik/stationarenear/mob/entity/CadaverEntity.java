package dev.sixik.stationarenear.mob.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public class CadaverEntity extends PathfinderMob implements GeoEntity {

    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(CadaverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> UNHIDING = SynchedEntityData.defineId(CadaverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RUNNING = SynchedEntityData.defineId(CadaverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int UNHIDE_TICKS = 32;
    private static final int HIDDEN_STALK_MIN_REVEAL_TICKS = 20 * 60;
    private static final int HIDDEN_STALK_MAX_REVEAL_TICKS = 20 * 90;
    private static final double REVEAL_RANGE = 5.0D;
    private static final double HIDDEN_STALK_RANGE = 14.0D;
    private static final double HIDDEN_STALK_SPEED = 0.72D;
    private static final double HIDDEN_STALK_STOP_DISTANCE_SQR = 2.25D;
    private static final double AGGRO_RANGE = 18.0D;
    private static final RawAnimation IDLE_HIDDEN_ANIMATION = RawAnimation.begin().thenLoop("idleHiden");
    private static final RawAnimation WALK_HIDDEN_ANIMATION = RawAnimation.begin().thenLoop("walkHiden");
    private static final RawAnimation UNHIDE_ANIMATION = RawAnimation.begin().thenPlay("unhide");
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN_ANIMATION = RawAnimation.begin().thenLoop("run");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int unhideTicks;
    private int hiddenStalkTicks;
    private int hiddenStalkRevealTicks;
    private UUID pendingTargetId;
    private UUID hiddenStalkTargetId;

    public CadaverEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, AGGRO_RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CadaverAttackGoal(this));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.55D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 7.0F));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HIDDEN, true);
        entityData.define(UNHIDING, false);
        entityData.define(RUNNING, false);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "movement", 4, state -> {
            if (isUnhiding()) {
                return state.setAndContinue(UNHIDE_ANIMATION);
            }
            if (isHidden()) {
                return state.setAndContinue(state.isMoving() ? WALK_HIDDEN_ANIMATION : IDLE_HIDDEN_ANIMATION);
            }
            if (isRunning()) {
                return state.setAndContinue(RUN_ANIMATION);
            }
            return state.setAndContinue(state.isMoving() ? WALK_ANIMATION : IDLE_ANIMATION);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void tick() {
        if (isUnhiding()) {
            setDeltaMovement(Vec3.ZERO);
            getNavigation().stop();
        }
        super.tick();
        if (isUnhiding()) {
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected void customServerAiStep() {
        if (isUnhiding()) {
            setRunning(false);
            getNavigation().stop();
            unhideTicks--;
            if (unhideTicks <= 0) {
                finishUnhide();
            }
            return;
        }
        super.customServerAiStep();
        if (isHidden()) {
            setRunning(false);
            tickHiddenStalking();
            return;
        }
        if (!isValidTarget(getTarget())) {
            setTarget(nearestScaryPlayer(AGGRO_RANGE).orElse(null));
        }
        setRunning(isValidTarget(getTarget()));
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (!level().isClientSide() && isHidden()) {
            LivingEntity attacker = damageSource.getEntity() instanceof LivingEntity living ? living : null;
            beginUnhide(attacker);
        }
        return super.hurt(damageSource, amount);
    }

    @Override
    public boolean isPushable() {
        return !isUnhiding() && super.isPushable();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("hidden", isHidden());
        tag.putBoolean("unhiding", isUnhiding());
        tag.putInt("unhideTicks", unhideTicks);
        tag.putInt("hiddenStalkTicks", hiddenStalkTicks);
        tag.putInt("hiddenStalkRevealTicks", hiddenStalkRevealTicks);
        if (pendingTargetId != null) {
            tag.putUUID("pendingTargetId", pendingTargetId);
        }
        if (hiddenStalkTargetId != null) {
            tag.putUUID("hiddenStalkTargetId", hiddenStalkTargetId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(HIDDEN, !tag.contains("hidden") || tag.getBoolean("hidden"));
        entityData.set(UNHIDING, tag.getBoolean("unhiding"));
        unhideTicks = tag.getInt("unhideTicks");
        hiddenStalkTicks = tag.getInt("hiddenStalkTicks");
        hiddenStalkRevealTicks = tag.contains("hiddenStalkRevealTicks") ? tag.getInt("hiddenStalkRevealTicks") : 0;
        pendingTargetId = tag.hasUUID("pendingTargetId") ? tag.getUUID("pendingTargetId") : null;
        hiddenStalkTargetId = tag.hasUUID("hiddenStalkTargetId") ? tag.getUUID("hiddenStalkTargetId") : null;
    }

    public boolean isHidden() {
        return entityData.get(HIDDEN);
    }

    public boolean isUnhiding() {
        return entityData.get(UNHIDING);
    }

    public boolean isRunning() {
        return entityData.get(RUNNING);
    }

    private void setRunning(boolean running) {
        entityData.set(RUNNING, running);
    }

    private void beginUnhide(LivingEntity target) {
        if (isUnhiding() || !isHidden()) {
            return;
        }
        entityData.set(HIDDEN, false);
        entityData.set(UNHIDING, true);
        setRunning(false);
        unhideTicks = UNHIDE_TICKS;
        hiddenStalkTicks = 0;
        hiddenStalkRevealTicks = 0;
        hiddenStalkTargetId = null;
        pendingTargetId = target != null ? target.getUUID() : null;
        lookAtUnhideTarget(target);
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
    }

    private void lookAtUnhideTarget(LivingEntity target) {
        if (target == null) {
            return;
        }
        Vec3 direction = target.position().subtract(position());
        if (direction.lengthSqr() < 0.001D) {
            return;
        }
        float yaw = (float) (Math.atan2(direction.z, direction.x) * 180.0D / Math.PI) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        yRotO = yaw;
        yHeadRotO = yaw;
        yBodyRotO = yaw;
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }


    private void tickHiddenStalking() {
        Player target = hiddenStalkTarget();
        if (!isValidHiddenStalkTarget(target)) {
            target = nearestVisibleScaryPlayer(HIDDEN_STALK_RANGE).orElse(null);
            hiddenStalkTargetId = target != null ? target.getUUID() : null;
            hiddenStalkTicks = 0;
            hiddenStalkRevealTicks = target != null ? randomHiddenStalkRevealTicks() : 0;
        }
        if (target == null) {
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (distanceToSqr(target) > HIDDEN_STALK_STOP_DISTANCE_SQR) {
            getNavigation().moveTo(target, HIDDEN_STALK_SPEED);
        } else {
            getNavigation().stop();
        }
        if (hiddenStalkRevealTicks <= 0) {
            hiddenStalkRevealTicks = randomHiddenStalkRevealTicks();
        }
        hiddenStalkTicks++;
        if (hiddenStalkTicks >= hiddenStalkRevealTicks) {
            beginUnhide(target);
        }
    }

    private int randomHiddenStalkRevealTicks() {
        return HIDDEN_STALK_MIN_REVEAL_TICKS + random.nextInt(HIDDEN_STALK_MAX_REVEAL_TICKS - HIDDEN_STALK_MIN_REVEAL_TICKS + 1);
    }

    private Player hiddenStalkTarget() {
        return hiddenStalkTargetId != null ? level().getPlayerByUUID(hiddenStalkTargetId) : null;
    }

    private boolean isValidHiddenStalkTarget(Player player) {
        return player != null
                && isScary(player)
                && player.isAlive()
                && distanceToSqr(player) <= HIDDEN_STALK_RANGE * HIDDEN_STALK_RANGE
                && hasLineOfSight(player);
    }

    private Optional<Player> nearestVisibleScaryPlayer(double range) {
        AABB area = getBoundingBox().inflate(range);
        return level().getEntitiesOfClass(Player.class, area, this::isValidHiddenStalkTarget)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr));
    }

    private void finishUnhide() {
        entityData.set(UNHIDING, false);
        LivingEntity pendingTarget = pendingTarget();
        setTarget(isValidTarget(pendingTarget) ? pendingTarget : nearestScaryPlayer(AGGRO_RANGE).orElse(null));
        setRunning(isValidTarget(getTarget()));
        pendingTargetId = null;
    }

    private LivingEntity pendingTarget() {
        if (pendingTargetId == null) {
            return null;
        }
        return level().getPlayerByUUID(pendingTargetId);
    }

    private Optional<Player> nearestScaryPlayer(double range) {
        AABB area = getBoundingBox().inflate(range);
        return level().getEntitiesOfClass(Player.class, area, CadaverEntity::isScary)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr));
    }

    private boolean isValidTarget(LivingEntity target) {
        return target instanceof Player player
                && isScary(player)
                && target.isAlive()
                && distanceToSqr(target) <= AGGRO_RANGE * AGGRO_RANGE;
    }

    private static boolean isScary(Player player) {
        return player != null && !player.isCreative() && !player.isSpectator();
    }

    private static final class CadaverAttackGoal extends Goal {
        private final CadaverEntity cadaver;
        private int attackCooldown;

        private CadaverAttackGoal(CadaverEntity cadaver) {
            this.cadaver = cadaver;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !cadaver.isHidden() && !cadaver.isUnhiding() && cadaver.isValidTarget(cadaver.getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = cadaver.getTarget();
            if (!cadaver.isValidTarget(target)) {
                return;
            }
            cadaver.setRunning(true);
            cadaver.getLookControl().setLookAt(target, 30.0F, 30.0F);
            cadaver.getNavigation().moveTo(target, 1.2D);
            if (attackCooldown > 0) {
                attackCooldown--;
            }
            if (attackCooldown <= 0 && cadaver.distanceToSqr(target) <= 2.4D) {
                attackCooldown = 20;
                cadaver.doHurtTarget(target);
            }
        }
    }
}
