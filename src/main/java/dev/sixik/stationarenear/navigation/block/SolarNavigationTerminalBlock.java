package dev.sixik.stationarenear.navigation.block;

import dev.sixik.stationarenear.navigation.network.SolarNavigationNetwork;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class SolarNavigationTerminalBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);

    private static final int MASTER_PART_X = 1;
    private static final long TERMINAL_SEED_SALT = 0x5EED_51A7_5A17L;

    public SolarNavigationTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_X, MASTER_PART_X));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        if (!canPlaceTerminal(context.getLevel(), masterPos, facing, context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART_X, MASTER_PART_X);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !isMaster(state)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Direction side = facing.getClockWise();
        for (int sideOffset = -1; sideOffset <= 1; sideOffset++) {
            if (sideOffset == 0) {
                continue;
            }
            level.setBlock(pos.relative(side, sideOffset), state.setValue(PART_X, MASTER_PART_X + sideOffset), 3);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos masterPos = masterPos(pos, state);
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            SolarNavigationNetwork.openTerminal(serverPlayer, masterPos, terminalSeed(serverLevel, masterPos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel && !oldState.is(state.getBlock()) && isMaster(state)) {
            ShipDockingAnchorResolver.bindNearbyShip(serverLevel, pos);
        }
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            ShipDockingAnchorSavedData.get(serverLevel).remove(masterPos(pos, state));
        }
        super.destroy(level, pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(state.getBlock())) {
            removeTerminalParts(level, masterPos(pos, state), state.getValue(FACING), pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }


    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isMaster(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
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
        builder.add(FACING, PART_X);
    }

    public static boolean isMaster(BlockState state) {
        return state.hasProperty(PART_X) && state.getValue(PART_X) == MASTER_PART_X;
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction side = state.getValue(FACING).getClockWise();
        int sideOffset = state.getValue(PART_X) - MASTER_PART_X;
        return pos.relative(side, -sideOffset);
    }

    public static long terminalSeed(ServerLevel level, BlockPos pos) {
        return level.getSeed() ^ Mth.getSeed(pos) ^ TERMINAL_SEED_SALT;
    }

    private static boolean canPlaceTerminal(LevelAccessor level, BlockPos masterPos, Direction facing, BlockPlaceContext context) {
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            BlockState partState = level.getBlockState(partPos);
            if (!partPos.equals(masterPos) && !partState.canBeReplaced(context)) {
                return false;
            }
        }
        return true;
    }

    private static Set<BlockPos> partPositions(BlockPos masterPos, Direction facing) {
        Set<BlockPos> positions = new HashSet<>(3);
        Direction side = facing.getClockWise();
        for (int sideOffset = -1; sideOffset <= 1; sideOffset++) {
            positions.add(masterPos.relative(side, sideOffset));
        }
        return positions;
    }

    private static void removeTerminalParts(Level level, BlockPos masterPos, Direction facing, BlockPos sourcePos) {
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            if (partPos.equals(sourcePos)) {
                continue;
            }
            BlockState partState = level.getBlockState(partPos);
            if (partState.getBlock() instanceof SolarNavigationTerminalBlock) {
                level.removeBlock(partPos, false);
            }
        }
    }
}
