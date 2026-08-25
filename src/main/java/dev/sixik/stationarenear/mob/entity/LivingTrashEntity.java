package dev.sixik.stationarenear.mob.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LivingTrashEntity extends PathfinderMob implements GeoEntity {

    private static final EntityDataAccessor<Boolean> HIDING = SynchedEntityData.defineId(LivingTrashEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> CONTAINER_POS = SynchedEntityData.defineId(LivingTrashEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> HIDDEN_SLOT = SynchedEntityData.defineId(LivingTrashEntity.class, EntityDataSerializers.INT);
    private static final double PLAYER_DETECT_RANGE = 9.0D;
    private static final int CONTAINER_SEARCH_RADIUS = 2;
    private static final double CONTAINER_HIDE_REACH_SQR = 2.25D;
    private static final int MAX_SPRINT_STAMINA_TICKS = 20 * 8;
    private static final int PANIC_RUN_TICKS = 20 * 3;
    private static final double FLEE_SPRINT_SPEED = 1.45D;
    private static final double FLEE_TIRED_SPEED = 0.82D;
    private static final double WANDER_SPEED = 0.9D;
    private static final double HIDDEN_PLAYER_RELEASE_RANGE = 30.0D;
    private static final int HIDDEN_EMPTY_AREA_RELEASE_TICKS = 20 * 10;
    private static final int HIDDEN_SLOT_ACTION_MIN_TICKS = 20;
    private static final int HIDDEN_SLOT_ACTION_RANDOM_TICKS = 50;
    private static final int IDLE_CONTAINER_HIDE_CHANCE = 90;
    private static final int MEMORY_CONTAINER_SCAN_RADIUS = 8;
    private static final double REMEMBERED_CONTAINER_PATH_REACH_SQR = 3.0D;
    private static final int FOUND_CONTAINER_AVOID_TICKS = 20 * 20;
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final List<ItemStack> eatenItems = new ArrayList<>();
    private final Map<BlockPos, Boolean> rememberedContainers = new HashMap<>();
    private int hideCooldown;
    private int sprintStaminaTicks = MAX_SPRINT_STAMINA_TICKS;
    private int panicRunTicks;
    private int hiddenNoNearbyPlayerTicks;
    private int hiddenSlotActionTicks;
    private int foundContainerAvoidTicks;
    private BlockPos foundContainerToAvoid;
    private Vec3 panicAwayFrom;

    public LivingTrashEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (isHiding()) {
                return state.setAndContinue(IDLE_ANIMATION);
            }
            return state.setAndContinue(state.isMoving() ? WALK_ANIMATION : IDLE_ANIMATION);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleePlayerGoal(this));
        goalSelector.addGoal(4, new RestlessWanderGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HIDING, false);
        entityData.define(CONTAINER_POS, Optional.empty());
        entityData.define(HIDDEN_SLOT, -1);
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            tickFoundContainerAvoid();
        }
        if (isHiding()) {
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            noPhysics = true;
            Optional<BlockPos> containerPos = containerPos();
            if (!level().isClientSide()) {
                if (containerPos.isEmpty() || !isContainer(containerPos.get()) || !ensureHiddenSlot(containerPos.get())) {
                    releaseFromContainer();
                } else {
                    tickHiddenContainer(containerPos.get());
                    tickHiddenAutoRelease(containerPos.get());
                }
            }
        }
        super.tick();
        if (isHiding()) {
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected void customServerAiStep() {
        if (isHiding()) {
            return;
        }
        super.customServerAiStep();
        if (hideCooldown > 0) {
            hideCooldown--;
        }
        Player nearestPlayer = nearestScaryPlayer();
        if (panicRunTicks > 0) {
            panicRunTicks--;
            if (panicRunTicks <= 0) {
                panicAwayFrom = null;
            }
        }
        if (nearestPlayer == null && panicRunTicks <= 0 && sprintStaminaTicks < MAX_SPRINT_STAMINA_TICKS) {
            sprintStaminaTicks++;
        }
        if (nearestPlayer != null && hideCooldown <= 0) {
            tryHideInNearbyContainer(true);
        } else if (nearestPlayer == null && hideCooldown <= 0 && random.nextInt(IDLE_CONTAINER_HIDE_CHANCE) == 0) {
            tryHideInNearbyContainer(false);
        }
    }

    @Override
    public boolean isInvisible() {
        return isHiding() || super.isInvisible();
    }

    @Override
    public boolean isPushable() {
        return !isHiding() && super.isPushable();
    }

    @Override
    public boolean isPickable() {
        return !isHiding() && super.isPickable();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("hiding", isHiding());
        containerPos().ifPresent(pos -> tag.putLong("containerPos", pos.asLong()));
        tag.putInt("hiddenSlot", hiddenSlot());
        tag.putInt("hideCooldown", hideCooldown);
        tag.putInt("sprintStaminaTicks", sprintStaminaTicks);
        tag.putInt("panicRunTicks", panicRunTicks);
        tag.putInt("hiddenNoNearbyPlayerTicks", hiddenNoNearbyPlayerTicks);
        tag.putInt("hiddenSlotActionTicks", hiddenSlotActionTicks);
        tag.putInt("foundContainerAvoidTicks", foundContainerAvoidTicks);
        if (foundContainerToAvoid != null) {
            tag.putLong("foundContainerToAvoid", foundContainerToAvoid.asLong());
        }
        ListTag eatenTag = new ListTag();
        for (ItemStack stack : eatenItems) {
            if (!stack.isEmpty()) {
                eatenTag.add(stack.save(new CompoundTag()));
            }
        }
        tag.put("eatenItems", eatenTag);
        ListTag rememberedTag = new ListTag();
        rememberedContainers.forEach((pos, hasLoot) -> {
            CompoundTag containerTag = new CompoundTag();
            containerTag.putLong("pos", pos.asLong());
            containerTag.putBoolean("hasLoot", hasLoot);
            rememberedTag.add(containerTag);
        });
        tag.put("rememberedContainers", rememberedTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(HIDING, tag.getBoolean("hiding"));
        entityData.set(CONTAINER_POS, tag.contains("containerPos") ? Optional.of(BlockPos.of(tag.getLong("containerPos"))) : Optional.empty());
        entityData.set(HIDDEN_SLOT, tag.contains("hiddenSlot") ? tag.getInt("hiddenSlot") : -1);
        hideCooldown = tag.getInt("hideCooldown");
        sprintStaminaTicks = tag.contains("sprintStaminaTicks") ? Math.min(tag.getInt("sprintStaminaTicks"), MAX_SPRINT_STAMINA_TICKS) : MAX_SPRINT_STAMINA_TICKS;
        panicRunTicks = tag.getInt("panicRunTicks");
        hiddenNoNearbyPlayerTicks = tag.getInt("hiddenNoNearbyPlayerTicks");
        hiddenSlotActionTicks = tag.getInt("hiddenSlotActionTicks");
        foundContainerAvoidTicks = tag.getInt("foundContainerAvoidTicks");
        foundContainerToAvoid = tag.contains("foundContainerToAvoid") ? BlockPos.of(tag.getLong("foundContainerToAvoid")) : null;
        if (foundContainerAvoidTicks <= 0) {
            foundContainerToAvoid = null;
        }
        eatenItems.clear();
        ListTag eatenTag = tag.getList("eatenItems", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < eatenTag.size(); index++) {
            ItemStack stack = ItemStack.of(eatenTag.getCompound(index));
            if (!stack.isEmpty()) {
                eatenItems.add(stack);
            }
        }
        rememberedContainers.clear();
        ListTag rememberedTag = tag.getList("rememberedContainers", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < rememberedTag.size(); index++) {
            CompoundTag containerTag = rememberedTag.getCompound(index);
            rememberedContainers.put(BlockPos.of(containerTag.getLong("pos")), containerTag.getBoolean("hasLoot"));
        }
        noPhysics = isHiding();
        setNoGravity(isHiding());
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource damageSource, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(damageSource, looting, recentlyHit);
        for (ItemStack stack : eatenItems) {
            if (!stack.isEmpty()) {
                spawnAtLocation(stack.copy());
            }
        }
        eatenItems.clear();
    }

    public boolean isHiding() {
        return entityData.get(HIDING);
    }

    public Optional<BlockPos> containerPos() {
        return entityData.get(CONTAINER_POS);
    }

    public int hiddenSlot() {
        return entityData.get(HIDDEN_SLOT);
    }

    public void releaseFromContainer() {
        releaseFromContainer(nearestScaryPlayer(), false);
    }

    public void releaseFromContainer(Player scarePlayer) {
        releaseFromContainer(scarePlayer, false);
    }

    public void releaseFromContainer(Player scarePlayer, boolean foundByPlayer) {
        Optional<BlockPos> oldContainer = containerPos();
        entityData.set(HIDING, false);
        entityData.set(CONTAINER_POS, Optional.empty());
        entityData.set(HIDDEN_SLOT, -1);
        noPhysics = false;
        setNoGravity(false);
        hideCooldown = 20;
        hiddenNoNearbyPlayerTicks = 0;
        restoreSprintStamina();
        if (!level().isClientSide()) {
            if (foundByPlayer) {
                oldContainer.ifPresent(this::avoidFoundContainer);
            }
            BlockPos pos = oldContainer.orElse(blockPosition());
            moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, getYRot(), getXRot());
            Player player = scarePlayer != null ? scarePlayer : nearestScaryPlayer();
            if (player != null) {
                Vec3 away = position().subtract(player.position());
                if (away.lengthSqr() > 0.001D) {
                    setDeltaMovement(away.normalize().scale(0.75D).add(0.0D, 0.2D, 0.0D));
                    hurtMarked = true;
                }
                startPanicRun(player.position());
            }
        }
    }

    public static boolean releaseHiddenNear(ServerPlayer player, BlockPos containerPos, int hoveredSlot) {
        if (player.distanceToSqr(Vec3.atCenterOf(containerPos)) > 64.0D) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(16.0D);
        List<LivingTrashEntity> trash = level.getEntitiesOfClass(LivingTrashEntity.class, area, entity ->
                entity.isHiding()
                        && entity.hiddenSlot() == hoveredSlot
                        && entity.containerPos().filter(containerPos::equals).isPresent());
        if (trash.isEmpty()) {
            return false;
        }
        trash.get(0).releaseFromContainer(player, true);
        return true;
    }

    private void restoreSprintStamina() {
        sprintStaminaTicks = MAX_SPRINT_STAMINA_TICKS;
    }

    private void avoidFoundContainer(BlockPos containerPos) {
        foundContainerToAvoid = containerPos.immutable();
        foundContainerAvoidTicks = FOUND_CONTAINER_AVOID_TICKS;
    }

    private void tickFoundContainerAvoid() {
        if (foundContainerAvoidTicks <= 0) {
            foundContainerToAvoid = null;
            return;
        }
        foundContainerAvoidTicks--;
        if (foundContainerAvoidTicks <= 0) {
            foundContainerToAvoid = null;
        }
    }

    private boolean isFoundContainerAvoided(BlockPos containerPos) {
        return foundContainerAvoidTicks > 0 && foundContainerToAvoid != null && foundContainerToAvoid.equals(containerPos);
    }

    private void startPanicRun(Vec3 awayFrom) {
        panicAwayFrom = awayFrom;
        panicRunTicks = PANIC_RUN_TICKS;
        Vec3 target = DefaultRandomPos.getPosAway(this, 12, 5, awayFrom);
        if (target != null) {
            navigation.moveTo(target.x, target.y, target.z, FLEE_SPRINT_SPEED);
        }
    }

    private boolean hasPanicRun() {
        return panicRunTicks > 0 && panicAwayFrom != null;
    }

    private double fleeSpeed() {
        return sprintStaminaTicks > 0 ? FLEE_SPRINT_SPEED : FLEE_TIRED_SPEED;
    }

    private void consumeFleeStamina() {
        if (sprintStaminaTicks > 0) {
            sprintStaminaTicks--;
        }
    }

    private void tickHiddenAutoRelease(BlockPos containerPos) {
        if (hasPlayerNearContainer(containerPos)) {
            hiddenNoNearbyPlayerTicks = 0;
            return;
        }
        hiddenNoNearbyPlayerTicks++;
        if (hiddenNoNearbyPlayerTicks >= HIDDEN_EMPTY_AREA_RELEASE_TICKS) {
            releaseFromContainer();
        }
    }

    private boolean hasPlayerNearContainer(BlockPos containerPos) {
        AABB area = new AABB(containerPos).inflate(HIDDEN_PLAYER_RELEASE_RANGE);
        return !level().getEntitiesOfClass(Player.class, area, player -> !player.isSpectator()).isEmpty();
    }

    private void tickHiddenContainer(BlockPos containerPos) {
        BlockEntity blockEntity = level().getBlockEntity(containerPos);
        if (!(blockEntity instanceof Container inventory)) {
            return;
        }
        rememberContainer(containerPos, inventory);
        if (isContainerOpenedByPlayer(containerPos, inventory)) {
            return;
        }
        if (hiddenSlotActionTicks > 0) {
            hiddenSlotActionTicks--;
            return;
        }
        eatFromHiddenSlot(containerPos, inventory);
        moveHiddenSlot(containerPos, inventory);
        resetHiddenSlotActionTimer();
    }

    private boolean eatFromHiddenSlot(BlockPos containerPos, Container inventory) {
        int slot = hiddenSlot();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return false;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack eaten = stack.copy();
        eaten.setCount(1);
        eatenItems.add(eaten);
        stack.shrink(1);
        inventory.setChanged();
        rememberContainer(containerPos, inventory);
        return true;
    }

    private boolean isContainerOpenedByPlayer(BlockPos containerPos, Container inventory) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.isSpectator() || player.containerMenu == player.inventoryMenu) {
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(containerPos)) > 64.0D) {
                continue;
            }
            boolean menuUsesContainer = false;
            boolean menuHasValidForeignContainer = false;
            for (Slot slot : player.containerMenu.slots) {
                if (slot.container == player.getInventory()) {
                    continue;
                }
                if (slot.container == inventory) {
                    menuUsesContainer = true;
                    break;
                }
                if (slot.container.stillValid(player)) {
                    menuHasValidForeignContainer = true;
                }
            }
            if (menuUsesContainer || menuHasValidForeignContainer) {
                return true;
            }
        }
        return false;
    }

    private void moveHiddenSlot(BlockPos containerPos, Container inventory) {
        if (inventory.getContainerSize() <= 1) {
            return;
        }
        int currentSlot = hiddenSlot();
        for (int attempt = 0; attempt < 8; attempt++) {
            int nextSlot = random.nextInt(inventory.getContainerSize());
            if (nextSlot == currentSlot || isHiddenSlotReserved(containerPos, nextSlot)) {
                continue;
            }
            entityData.set(HIDDEN_SLOT, nextSlot);
            return;
        }
    }

    private void resetHiddenSlotActionTimer() {
        hiddenSlotActionTicks = HIDDEN_SLOT_ACTION_MIN_TICKS + random.nextInt(HIDDEN_SLOT_ACTION_RANDOM_TICKS + 1);
    }

    private void tryHideInNearbyContainer() {
        tryHideInNearbyContainer(false);
    }

    private void tryHideInNearbyContainer(boolean danger) {
        scanNearbyContainers();
        Optional<BlockPos> container = nearestContainer(danger);
        if (container.isEmpty()) {
            hideCooldown = 10;
            return;
        }

        BlockEntity blockEntity = level().getBlockEntity(container.get());
        if (!(blockEntity instanceof Container inventory)) {
            rememberedContainers.remove(container.get());
            hideCooldown = 10;
            return;
        }
        rememberContainer(container.get(), inventory);
        int slot = randomHideSlot(container.get(), inventory).orElse(-1);
        if (slot < 0) {
            hideCooldown = 10;
            return;
        }
        entityData.set(HIDING, true);
        entityData.set(CONTAINER_POS, Optional.of(container.get().immutable()));
        entityData.set(HIDDEN_SLOT, slot);
        hiddenNoNearbyPlayerTicks = 0;
        eatFromHiddenSlot(container.get(), inventory);
        resetHiddenSlotActionTimer();
        navigation.stop();
        setDeltaMovement(Vec3.ZERO);
        moveTo(container.get().getX() + 0.5D, container.get().getY() + 0.5D, container.get().getZ() + 0.5D, getYRot(), getXRot());
        setNoGravity(true);
        noPhysics = true;
    }

    private void scanNearbyContainers() {
        BlockPos origin = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-MEMORY_CONTAINER_SCAN_RADIUS, -3, -MEMORY_CONTAINER_SCAN_RADIUS),
                origin.offset(MEMORY_CONTAINER_SCAN_RADIUS, 3, MEMORY_CONTAINER_SCAN_RADIUS))) {
            BlockPos immutable = pos.immutable();
            BlockEntity blockEntity = level().getBlockEntity(immutable);
            if (blockEntity instanceof Container inventory) {
                rememberContainer(immutable, inventory);
            }
        }
    }

    private void rememberContainer(BlockPos pos, Container inventory) {
        rememberedContainers.put(pos.immutable(), hasLoot(inventory));
    }

    private boolean hasLoot(Container inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Optional<Integer> randomHideSlot(BlockPos containerPos, Container inventory) {
        Optional<Integer> emptySlot = randomSlot(containerPos, inventory, true);
        return emptySlot.isPresent() ? emptySlot : randomSlot(containerPos, inventory, false);
    }

    private Optional<Integer> randomEmptySlot(BlockPos containerPos, Container inventory) {
        return randomSlot(containerPos, inventory, true);
    }

    private Optional<Integer> randomSlot(BlockPos containerPos, Container inventory, boolean emptyOnly) {
        int availableCount = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isHiddenSlotReserved(containerPos, slot)) {
                continue;
            }
            if (emptyOnly != inventory.getItem(slot).isEmpty()) {
                continue;
            }
            availableCount++;
        }
        if (availableCount <= 0) {
            return Optional.empty();
        }

        int selected = random.nextInt(availableCount);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isHiddenSlotReserved(containerPos, slot)) {
                continue;
            }
            if (emptyOnly != inventory.getItem(slot).isEmpty()) {
                continue;
            }
            if (selected-- == 0) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }


    private boolean isHiddenSlotReserved(BlockPos containerPos, int slot) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return hiddenTrashInContainer(serverLevel, containerPos).stream()
                .anyMatch(entity -> entity != this && entity.hiddenSlot() == slot);
    }

    private boolean ensureHiddenSlot(BlockPos containerPos) {
        BlockEntity blockEntity = level().getBlockEntity(containerPos);
        if (!(blockEntity instanceof Container inventory)) {
            return false;
        }
        int slot = hiddenSlot();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return false;
        }
        if (ownsHiddenSlot(containerPos, slot)) {
            return true;
        }
        Optional<Integer> freeSlot = randomEmptySlot(containerPos, inventory);
        if (freeSlot.isEmpty()) {
            return false;
        }
        entityData.set(HIDDEN_SLOT, freeSlot.get());
        return true;
    }

    private boolean ownsHiddenSlot(BlockPos containerPos, int slot) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        LivingTrashEntity owner = null;
        for (LivingTrashEntity entity : hiddenTrashInContainer(serverLevel, containerPos)) {
            if (entity.hiddenSlot() != slot) {
                continue;
            }
            if (owner == null || entity.getId() < owner.getId()) {
                owner = entity;
            }
        }
        return owner == null || owner == this;
    }

    private List<LivingTrashEntity> hiddenTrashInContainer(ServerLevel serverLevel, BlockPos containerPos) {
        return serverLevel.getEntitiesOfClass(LivingTrashEntity.class, new AABB(containerPos).inflate(16.0D), entity ->
                entity.isHiding() && entity.containerPos().filter(containerPos::equals).isPresent());
    }

    private Optional<BlockPos> nearestContainer(boolean danger) {
        BlockPos nearestRemembered = nearestRememberedContainer(danger, true);
        if (nearestRemembered != null) {
            return Optional.of(nearestRemembered);
        }

        BlockPos origin = blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-CONTAINER_SEARCH_RADIUS, -2, -CONTAINER_SEARCH_RADIUS),
                origin.offset(CONTAINER_SEARCH_RADIUS, 2, CONTAINER_SEARCH_RADIUS))) {
            BlockPos immutable = pos.immutable();
            double distance = distanceToSqr(Vec3.atCenterOf(immutable));
            if (isFoundContainerAvoided(immutable) || distance > CONTAINER_HIDE_REACH_SQR || !isContainer(immutable)) {
                continue;
            }
            BlockEntity blockEntity = level().getBlockEntity(immutable);
            if (blockEntity instanceof Container inventory) {
                boolean hasLoot = hasLoot(inventory);
                rememberContainer(immutable, inventory);
                if (!danger && !hasLoot) {
                    continue;
                }
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = immutable;
            }
        }
        return Optional.ofNullable(best);
    }

    private BlockPos nearestRememberedContainer(boolean danger, boolean onlyReachable) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        List<BlockPos> invalid = new ArrayList<>();
        for (Map.Entry<BlockPos, Boolean> entry : rememberedContainers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (isFoundContainerAvoided(pos) || !danger && !entry.getValue()) {
                continue;
            }
            BlockEntity blockEntity = level().getBlockEntity(pos);
            if (!(blockEntity instanceof Container inventory)) {
                invalid.add(pos);
                continue;
            }
            rememberContainer(pos, inventory);
            if (!danger && !hasLoot(inventory)) {
                continue;
            }
            double distance = distanceToSqr(Vec3.atCenterOf(pos));
            if (onlyReachable && distance > REMEMBERED_CONTAINER_PATH_REACH_SQR) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        invalid.forEach(rememberedContainers::remove);
        return best;
    }

    private boolean isContainer(BlockPos pos) {
        BlockEntity blockEntity = level().getBlockEntity(pos);
        return blockEntity instanceof Container;
    }

    private Player nearestScaryPlayer() {
        return level().getNearestPlayer(this, PLAYER_DETECT_RANGE);
    }

    private static boolean isScary(Player player) {
        return player != null && !player.isCreative() && !player.isSpectator();
    }

    private static final class FleePlayerGoal extends Goal {
        private final LivingTrashEntity trash;
        private Player threat;

        private FleePlayerGoal(LivingTrashEntity trash) {
            this.trash = trash;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (trash.isHiding()) {
                return false;
            }
            threat = trash.nearestScaryPlayer();
            return isScary(threat) || trash.hasPanicRun();
        }

        @Override
        public boolean canContinueToUse() {
            return !trash.isHiding()
                    && (trash.hasPanicRun()
                    || isScary(threat) && threat.distanceToSqr(trash) < 14.0D * 14.0D);
        }

        @Override
        public void tick() {
            if (!isScary(threat)) {
                threat = trash.nearestScaryPlayer();
            }
            Vec3 awayFrom = isScary(threat) ? threat.position() : trash.panicAwayFrom;
            if (awayFrom == null) {
                return;
            }
            if (isScary(threat)) {
                trash.getLookControl().setLookAt(threat, 30.0F, 30.0F);
                if (trash.hideCooldown <= 0 && trash.tickCount % 5 == 0) {
                    trash.tryHideInNearbyContainer(true);
                    if (trash.isHiding()) {
                        return;
                    }
                }
            }
            trash.consumeFleeStamina();
            if (!trash.getNavigation().isDone() && trash.tickCount % 10 != 0) {
                return;
            }
            BlockPos rememberedContainer = trash.nearestRememberedContainer(true, false);
            Vec3 target = rememberedContainer != null
                    ? Vec3.atCenterOf(rememberedContainer)
                    : DefaultRandomPos.getPosAway(trash, 10, 5, awayFrom);
            if (target != null) {
                trash.getNavigation().moveTo(target.x, target.y, target.z, trash.fleeSpeed());
            }
        }
    }

    private static final class RestlessWanderGoal extends Goal {
        private final LivingTrashEntity trash;

        private RestlessWanderGoal(LivingTrashEntity trash) {
            this.trash = trash;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !trash.isHiding() && !trash.hasPanicRun() && trash.nearestScaryPlayer() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (!trash.getNavigation().isDone() && trash.tickCount % 20 != 0) {
                return;
            }
            Vec3 target = DefaultRandomPos.getPos(trash, 10, 4);
            if (target != null) {
                trash.getNavigation().moveTo(target.x, target.y, target.z, WANDER_SPEED);
            }
        }
    }
}


