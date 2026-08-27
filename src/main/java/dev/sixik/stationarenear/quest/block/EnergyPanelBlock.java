package dev.sixik.stationarenear.quest.block;

import dev.sixik.stationarenear.quest.event.EnergyPanelRepairedEvent;
import dev.sixik.stationarenear.quest.registry.QuestItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

public class EnergyPanelBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private static final VoxelShape NORTH_SHAPE = Block.box(0.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 16.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public EnergyPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, true));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace()
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(POWERED, true);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (state.getValue(POWERED)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("????????????? ????? ??? ????????."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!heldItem.is(QuestItems.ENGINEERING_GEAR.get())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("????? ?????????? ????????."), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(POWERED, true), 3);
            if (player instanceof ServerPlayer serverPlayer && !serverPlayer.getAbilities().instabuild) {
                heldItem.hurtAndBreak(1, serverPlayer, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
            if (level instanceof ServerLevel serverLevel) {
                MinecraftForge.EVENT_BUS.post(new EnergyPanelRepairedEvent(serverLevel, player, pos));
            }
            player.displayClientMessage(Component.literal("????????????? ????? ???????."), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
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
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }
}
