package dev.sixik.stationarenear.quest.block;

import dev.sixik.stationarenear.minigames.WireConnectionMinigameScreen;
import dev.sixik.stationarenear.quest.event.EnergyPanelRepairedEvent;
import dev.sixik.stationarenear.quest.network.QuestNetwork;
import dev.sixik.stationarenear.quest.registry.QuestItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    private static final VoxelShape NORTH_SHAPE = Block.box(0.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 16.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public EnergyPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(BROKEN, false));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace()
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(POWERED, false)
                .setValue(BROKEN, false);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(BROKEN)) {
            ItemStack heldItem = player.getItemInHand(hand);
            if (!heldItem.is(QuestItems.ELECTRICITY_REPAIR_KIT.get()) && !heldItem.is(QuestItems.ENGINEERING_GEAR.get())) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("Нужен набор электрика (Electricity Repair Kit) для починки щитка."), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (level.isClientSide) {
                WireConnectionMinigameScreen.open(() -> {
                    QuestNetwork.sendRepairEnergyPanel(pos);
                });
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            dev.sixik.stationarenear.quest.runtime.QuestFurniturePickupManager.hold(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void performRepair(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
        BlockState state = level.getBlockState(pos);
        if (state.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.ENERGY_PANEL.get()) && state.getValue(BROKEN)) {
            level.setBlock(pos, state.setValue(BROKEN, false).setValue(POWERED, false), 3);
            level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            ItemStack heldItem = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer && !serverPlayer.getAbilities().instabuild) {
                heldItem.hurtAndBreak(1, serverPlayer, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
            MinecraftForge.EVENT_BUS.post(new EnergyPanelRepairedEvent(level, player, pos));
            player.displayClientMessage(Component.literal("Электрический щиток починен и выключен."), true);
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) && !state.getValue(BROKEN) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(FACING).getOpposite() == side && state.getValue(POWERED) && !state.getValue(BROKEN) ? 15 : 0;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
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
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (player.isCreative()) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        if (player.getMainHandItem().isEmpty() || !player.hasCorrectToolForDrops(state)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, BROKEN);
    }
}
