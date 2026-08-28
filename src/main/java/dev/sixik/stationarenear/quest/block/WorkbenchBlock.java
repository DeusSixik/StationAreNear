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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class WorkbenchBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 1);

    private static final int MASTER_PART_X = 1;
    private static final int MASTER_PART_Y = 0;
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public WorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos masterPos = context.getClickedPos();
        if (!canPlaceWorkbench(context.getLevel(), masterPos, facing, context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART_X, MASTER_PART_X)
                .setValue(PART_Y, MASTER_PART_Y);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !isMaster(state)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (BlockPos partPos : partPositions(pos, facing)) {
            if (partPos.equals(pos)) {
                continue;
            }
            int partX = partX(pos, facing, partPos);
            int partY = partPos.getY() - pos.getY();
            level.setBlock(partPos, state.setValue(PART_X, partX).setValue(PART_Y, partY), 3);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isMaster(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
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
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos master = masterPos(pos, state);
        for (BlockPos partPos : partPositions(master, state.getValue(FACING))) {
            if (partPos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos master = masterPos(pos, state);
            removeWorkbenchParts(level, master, state.getValue(FACING), pos);
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
        builder.add(FACING, PART_X, PART_Y);
    }

    public static boolean isMaster(BlockState state) {
        return state.hasProperty(PART_X)
                && state.hasProperty(PART_Y)
                && state.getValue(PART_X) == MASTER_PART_X
                && state.getValue(PART_Y) == MASTER_PART_Y;
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction side = state.getValue(FACING).getClockWise();
        int sideOffset = state.getValue(PART_X) - MASTER_PART_X;
        int yOffset = state.getValue(PART_Y) - MASTER_PART_Y;
        return pos.relative(side, -sideOffset).below(yOffset);
    }

    private static boolean canPlaceWorkbench(LevelAccessor level, BlockPos masterPos, Direction facing, BlockPlaceContext context) {
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            BlockState partState = level.getBlockState(partPos);
            if (!partPos.equals(masterPos) && !partState.canBeReplaced(context)) {
                return false;
            }
        }
        return true;
    }

    private static Set<BlockPos> partPositions(BlockPos masterPos, Direction facing) {
        Set<BlockPos> positions = new HashSet<>(4);
        Direction side = facing.getClockWise();
        for (int sideOffset = -1; sideOffset <= 0; sideOffset++) {
            for (int yOffset = 0; yOffset <= 1; yOffset++) {
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

    private static void removeWorkbenchParts(Level level, BlockPos masterPos, Direction facing, BlockPos sourcePos) {
        for (BlockPos partPos : partPositions(masterPos, facing)) {
            if (partPos.equals(sourcePos)) {
                continue;
            }
            BlockState partState = level.getBlockState(partPos);
            if (partState.getBlock() instanceof WorkbenchBlock) {
                level.removeBlock(partPos, false);
            }
        }
    }
}
