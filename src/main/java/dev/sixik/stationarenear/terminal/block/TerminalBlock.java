package dev.sixik.stationarenear.terminal.block;

import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.terminal.block.entity.TerminalBlockEntity;
import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TerminalBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 1);

    private static final int MASTER_PART_Y = 0;

    public TerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_Y, MASTER_PART_Y));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        if (!canPlaceTerminal(context.getLevel(), masterPos, context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART_Y, MASTER_PART_Y);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !isMaster(state)) {
            return;
        }
        level.setBlock(pos.above(), state.setValue(PART_Y, 1), 3);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos masterPos = masterPos(pos, state);
        if (player instanceof ServerPlayer serverPlayer) {
            TerminalNetwork.openTerminal(serverPlayer, masterPos);
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
            removeTerminalParts(level, masterPos(pos, state), pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isMaster(state) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new TerminalBlockEntity(pos, state) : null;
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
        builder.add(FACING, PART_Y);
    }

    public static boolean isMaster(BlockState state) {
        return state.hasProperty(PART_Y) && state.getValue(PART_Y) == MASTER_PART_Y;
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART_Y) - MASTER_PART_Y);
    }

    private static boolean canPlaceTerminal(LevelAccessor level, BlockPos masterPos, BlockPlaceContext context) {
        BlockState topState = level.getBlockState(masterPos.above());
        return topState.canBeReplaced(context);
    }

    private static void removeTerminalParts(Level level, BlockPos masterPos, BlockPos sourcePos) {
        for (int yOffset = 0; yOffset <= 1; yOffset++) {
            BlockPos partPos = masterPos.above(yOffset);
            if (partPos.equals(sourcePos)) {
                continue;
            }
            BlockState partState = level.getBlockState(partPos);
            if (partState.getBlock() instanceof TerminalBlock) {
                level.removeBlock(partPos, false);
            }
        }
    }
}
