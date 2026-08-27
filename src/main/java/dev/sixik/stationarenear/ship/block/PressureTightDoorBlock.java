package dev.sixik.stationarenear.ship.block;

import dev.sixik.stationarenear.quest.registry.QuestItems;
import dev.sixik.stationarenear.ship.block.entity.PressureTightDoorBlockEntity;
import dev.sixik.stationarenear.ship.event.PressureTightDoorRepairedEvent;
import dev.sixik.stationarenear.ship.runtime.ShipIntegrityScanner;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class PressureTightDoorBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 2);

    private static final int MASTER_PART_X = 1;
    private static final int MASTER_PART_Y = 0;
    private static final VoxelShape NORTH_SOUTH_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape EAST_WEST_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public PressureTightDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(BROKEN, false)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        if (!canPlaceDoor(context.getLevel(), masterPos, facing, context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(OPEN, false)
                .setValue(BROKEN, false)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !isMaster(state)) {
            return;
        }

        for (BlockPos partPos : partPositions(pos, state.getValue(FACING))) {
            if (partPos.equals(pos)) {
                continue;
            }
            int partX = partX(pos, state.getValue(FACING), partPos);
            int partY = partPos.getY() - pos.getY();
            level.setBlock(partPos, state.setValue(PART_X, partX).setValue(PART_Y, partY), 3);
        }
        refreshShipIntegrity(level, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            ItemStack heldItem = player.getItemInHand(hand);
            if (isBroken(masterState) && heldItem.is(QuestItems.ENGINEERING_GEAR.get())) {
                repairDoor(level, master, masterState);
                if (player instanceof ServerPlayer serverPlayer && !serverPlayer.getAbilities().instabuild) {
                    heldItem.hurtAndBreak(1, serverPlayer, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
                }
                if (level instanceof ServerLevel serverLevel) {
                    MinecraftForge.EVENT_BUS.post(new PressureTightDoorRepairedEvent(serverLevel, player, master));
                }
                player.displayClientMessage(Component.literal("Pressure door repaired."), true);
                return InteractionResult.SUCCESS;
            }

            BlockEntity blockEntity = level.getBlockEntity(master);
            if (blockEntity instanceof PressureTightDoorBlockEntity door && !door.doorId().isBlank()) {
                String repairHint = isBroken(masterState) ? " | Repair: Engineering Gear" : "";
                player.displayClientMessage(Component.literal("Door ID: " + door.doorId() + " | Use terminal: door open " + door.doorId() + repairHint), true);
            } else {
                String repairHint = isBroken(masterState) ? " Repair with Engineering Gear." : "";
                player.displayClientMessage(Component.literal("Pressure door is terminal-controlled. Use: door open / door close." + repairHint), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean isMaster(BlockState state) {
        return state.hasProperty(PART_X)
                && state.hasProperty(PART_Y)
                && state.getValue(PART_X) == MASTER_PART_X
                && state.getValue(PART_Y) == MASTER_PART_Y;
    }

    public static boolean isOpen(BlockState state) {
        return state.hasProperty(OPEN) && state.getValue(OPEN);
    }

    public static boolean isBroken(BlockState state) {
        return state.hasProperty(BROKEN) && state.getValue(BROKEN);
    }

    public static boolean setOpen(Level level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PressureTightDoorBlock)) {
            return false;
        }

        BlockPos master = masterPos(pos, state);
        BlockState masterState = level.getBlockState(master);
        if (!(masterState.getBlock() instanceof PressureTightDoorBlock)) {
            return false;
        }
        if (open && isBroken(masterState)) {
            return false;
        }

        setDoorOpen(level, master, masterState, open);
        return true;
    }

    public static boolean placeDoor(Level level, BlockPos masterPos, Direction facing, boolean broken, @Nullable String doorId) {
        return placeDoor(level, masterPos, facing, broken, false, doorId);
    }

    public static boolean setBroken(Level level, BlockPos pos, boolean broken) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PressureTightDoorBlock)) {
            return false;
        }

        BlockPos master = masterPos(pos, state);
        BlockState masterState = level.getBlockState(master);
        if (!(masterState.getBlock() instanceof PressureTightDoorBlock)) {
            return false;
        }

        setDoorBroken(level, master, masterState, broken);
        return true;
    }

    public static boolean placeDoor(Level level, BlockPos masterPos, Direction facing, boolean broken, boolean open, @Nullable String doorId) {
        BlockState state = level.getBlockState(masterPos);
        if (!(state.getBlock() instanceof PressureTightDoorBlock doorBlock)) {
            return false;
        }
        BlockState doorState = state
                .setValue(FACING, facing)
                .setValue(OPEN, !broken && open)
                .setValue(BROKEN, broken)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y);
        if (!canPlaceDoor(level, masterPos, facing, null)) {
            return false;
        }
        level.setBlock(masterPos, doorState, 3);
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            if (partPos.equals(masterPos)) {
                continue;
            }
            int partX = partX(masterPos, facing, partPos);
            int partY = partPos.getY() - masterPos.getY();
            level.setBlock(partPos, doorState.setValue(PART_X, partX).setValue(PART_Y, partY), 3);
        }
        BlockEntity blockEntity = level.getBlockEntity(masterPos);
        if (blockEntity instanceof PressureTightDoorBlockEntity pressureDoor) {
            pressureDoor.setDoorId(doorId);
            pressureDoor.markAnimationDirty();
        }
        doorBlock.afterDoorPlaced(level, masterPos);
        return true;
    }


    public static BlockPos masterPos(BlockPos partPos, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        int sideOffset = state.getValue(PART_X) - MASTER_PART_X;
        int yOffset = state.getValue(PART_Y) - MASTER_PART_Y;
        return partPos.relative(side, -sideOffset).below(yOffset);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isMaster(state) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new PressureTightDoorBlockEntity(pos, state) : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH_SHAPE : EAST_WEST_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : getShape(state, level, pos, context);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos master = masterPos(pos, state);
        return contains(partPositions(master, state.getValue(FACING)), pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos master = masterPos(pos, state);
            removeDoorParts(level, master, state.getValue(FACING), pos);
            refreshShipIntegrity(level, master);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, BROKEN, PART_X, PART_Y);
    }

    private static void setDoorOpen(Level level, BlockPos masterPos, BlockState masterState, boolean open) {
        Direction facing = masterState.getValue(FACING);
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            BlockState partState = level.getBlockState(partPos);
            if (!partState.is(masterState.getBlock())) {
                continue;
            }
            level.setBlock(partPos, partState.setValue(OPEN, open), 3);
        }
        BlockEntity blockEntity = level.getBlockEntity(masterPos);
        if (blockEntity instanceof PressureTightDoorBlockEntity door) {
            door.markAnimationDirty();
        }
        refreshShipIntegrity(level, masterPos);
    }

    private static void repairDoor(Level level, BlockPos masterPos, BlockState masterState) {
        setDoorBroken(level, masterPos, masterState, false);
    }

    private static void setDoorBroken(Level level, BlockPos masterPos, BlockState masterState, boolean broken) {
        Direction facing = masterState.getValue(FACING);
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            BlockState partState = level.getBlockState(partPos);
            if (!partState.is(masterState.getBlock())) {
                continue;
            }
            level.setBlock(partPos, partState.setValue(BROKEN, broken).setValue(OPEN, false), 3);
        }
        BlockEntity blockEntity = level.getBlockEntity(masterPos);
        if (blockEntity instanceof PressureTightDoorBlockEntity door) {
            door.markAnimationDirty();
        }
        refreshShipIntegrity(level, masterPos);
    }

    private static boolean canPlaceDoor(LevelAccessor level, BlockPos masterPos, Direction facing, BlockPlaceContext context) {
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            BlockState partState = level.getBlockState(partPos);
            if (!partPos.equals(masterPos) && context != null && !partState.canBeReplaced(context)) {
                return false;
            }
            if (!partPos.equals(masterPos) && context == null && !partState.isAir()) {
                return false;
            }
        }
        return true;
    }

    public static Set<BlockPos> partPositions(BlockPos masterPos, Direction facing) {
        Set<BlockPos> positions = new HashSet<>(9);
        Direction side = facing.getClockWise();
        for (int sideOffset = -1; sideOffset <= 1; sideOffset++) {
            for (int yOffset = 0; yOffset <= 2; yOffset++) {
                positions.add(masterPos.relative(side, sideOffset).above(yOffset));
            }
        }
        return positions;
    }

    private static int partX(BlockPos masterPos, Direction facing, BlockPos partPos) {
        Direction side = facing.getClockWise();
        int dx = partPos.getX() - masterPos.getX();
        int dz = partPos.getZ() - masterPos.getZ();
        return MASTER_PART_X + dx * side.getStepX() + dz * side.getStepZ();
    }

    private static void removeDoorParts(Level level, BlockPos masterPos, Direction facing, BlockPos sourcePos) {
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            if (partPos.equals(sourcePos)) {
                continue;
            }
            BlockState partState = level.getBlockState(partPos);
            if (partState.getBlock() instanceof PressureTightDoorBlock) {
                level.removeBlock(partPos, false);
            }
        }
    }

    private static boolean contains(Set<BlockPos> positions, BlockPos pos) {
        return positions.contains(pos);
    }

    protected void afterDoorPlaced(Level level, BlockPos masterPos) {
        refreshShipIntegrity(level, masterPos);
    }

    private static void refreshShipIntegrity(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (BlockPos terminalPos : ShipIntegrityScanner.terminalsForBlock(serverLevel, pos)) {
            ShipManager.updateDecompression(serverLevel, terminalPos);
        }
    }
}
