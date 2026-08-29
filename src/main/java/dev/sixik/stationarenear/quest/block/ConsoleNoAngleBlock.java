package dev.sixik.stationarenear.quest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ConsoleNoAngleBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape NORTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape SOUTH_SHAPE = NORTH_SHAPE;
    private static final VoxelShape EAST_SHAPE = NORTH_SHAPE;
    private static final VoxelShape WEST_SHAPE = NORTH_SHAPE;

    public ConsoleNoAngleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        }
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        BlockPos masterPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            BlockPos targetTerminal = resolveShipTerminal(serverPlayer.serverLevel(), masterPos);
            dev.sixik.stationarenear.terminal.network.TerminalNetwork.openTerminal(serverPlayer, targetTerminal);
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    public static BlockPos resolveShipTerminal(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.is(dev.sixik.stationarenear.terminal.registry.TerminalBlocks.TERMINAL.get())
                || currentState.is(dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())) {
            return pos;
        }

        dev.sixik.stationarenear.structures.world.StationSavedData stationData = dev.sixik.stationarenear.structures.world.StationSavedData.get(level);
        for (dev.sixik.stationarenear.structures.data.StationInstance station : stationData.stations()) {
            boolean inside = false;
            for (dev.sixik.stationarenear.structures.data.PlacedStationPiece piece : station.pieces()) {
                if (piece.bounds().inflatedBy(16).isInside(pos)) {
                    inside = true;
                    break;
                }
            }
            if (inside) {
                if (station.customData().contains(dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS)) {
                    BlockPos navPos = BlockPos.of(station.customData().getLong(dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner.KEY_NAVIGATION_TERMINAL_POS));
                    java.util.Optional<dev.sixik.stationarenear.ship.docking.ShipDockingAnchor> anchor = dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData.get(level).anchor(navPos);
                    if (anchor.isPresent()) {
                        for (Long related : dev.sixik.stationarenear.ship.runtime.ShipIntegrityScanner.relatedTerminalPositions(level, navPos, anchor.get())) {
                            BlockPos relatedPos = BlockPos.of(related);
                            if (level.getBlockState(relatedPos).is(dev.sixik.stationarenear.terminal.registry.TerminalBlocks.TERMINAL.get())) {
                                return relatedPos;
                            }
                        }
                    }
                    if (level.getBlockState(navPos).is(dev.sixik.stationarenear.navigation.registry.SolarNavigationBlocks.SOLAR_NAVIGATION_TERMINAL.get())
                            || level.getBlockState(navPos).is(dev.sixik.stationarenear.terminal.registry.TerminalBlocks.TERMINAL.get())) {
                        return navPos;
                    }
                }
            }
        }

        java.util.Optional<dev.sixik.stationarenear.ship.docking.ShipDockingAnchor> anchor = dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData.get(level)
                .anchor(pos)
                .or(() -> dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver.bindNearbyShip(level, pos));
        if (anchor.isPresent()) {
            for (Long related : dev.sixik.stationarenear.ship.runtime.ShipIntegrityScanner.relatedTerminalPositions(level, pos, anchor.get())) {
                BlockPos relatedPos = BlockPos.of(related);
                if (level.getBlockState(relatedPos).is(dev.sixik.stationarenear.terminal.registry.TerminalBlocks.TERMINAL.get())) {
                    return relatedPos;
                }
            }
        }

        return pos;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.MODEL : RenderShape.INVISIBLE;
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return pos.getY() < level.getMaxBuildHeight() - 1;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if ((direction == Direction.UP && half == DoubleBlockHalf.LOWER || direction == Direction.DOWN && half == DoubleBlockHalf.UPPER)
                && (!neighborState.is(this) || neighborState.getValue(HALF) == half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos otherPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this)) {
                level.removeBlock(otherPos, false);
            }
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
        builder.add(FACING, HALF);
    }
}
