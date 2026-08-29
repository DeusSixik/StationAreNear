package dev.sixik.stationarenear.quest.block;

import dev.sixik.stationarenear.minigames.SyncBatteryMinigameScreen;
import dev.sixik.stationarenear.quest.network.QuestNetwork;
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
import org.jetbrains.annotations.Nullable;

public class WallMountedPanelBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    private final VoxelShape northShape;
    private final VoxelShape eastShape;
    private final VoxelShape southShape;
    private final VoxelShape westShape;

    public WallMountedPanelBlock(Properties properties, double thickness) {
        super(properties);
        double clampedThickness = Math.max(1.0D, Math.min(16.0D, thickness));
        this.northShape = Block.box(0.0D, 0.0D, 16.0D - clampedThickness, 16.0D, 16.0D, 16.0D);
        this.eastShape = Block.box(0.0D, 0.0D, 0.0D, clampedThickness, 16.0D, 16.0D);
        this.southShape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, clampedThickness);
        this.westShape = Block.box(16.0D - clampedThickness, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
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
                .setValue(BROKEN, false);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(BROKEN)) {
            ItemStack heldItem = player.getItemInHand(hand);
            boolean isOxygen = state.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.OXYGEN_PANEL.get());
            boolean isGravitation = state.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.GRAVITATION_PANEL.get());

            boolean hasValidTool = (isOxygen && heldItem.is(QuestItems.OXYGEN_REPAIR_KIT.get()))
                    || (isGravitation && heldItem.is(QuestItems.GRAVITATION_REPAIR_KIT.get()))
                    || heldItem.is(QuestItems.ENGINEERING_GEAR.get());

            if (!hasValidTool) {
                if (!level.isClientSide) {
                    if (isOxygen) {
                        player.displayClientMessage(Component.literal("Нужен набор для ремонта кислородной панели (Oxygen Repair Kit)."), true);
                    } else if (isGravitation) {
                        player.displayClientMessage(Component.literal("Нужен набор для ремонта гравитационной панели (Gravitation Repair Kit)."), true);
                    } else {
                        player.displayClientMessage(Component.literal("Нужен ремонтный набор для починки панели."), true);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (level.isClientSide) {
                SyncBatteryMinigameScreen.open(() -> {
                    QuestNetwork.sendRepairWallPanel(pos);
                });
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("Панель исправна и работает в штатном режиме."), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            if (state.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.GRAVITATION_PANEL.get())) {
                dev.sixik.stationarenear.structures.gravity.StationGravitationManager.onPanelPlaced((ServerLevel) level, pos, state.getValue(BROKEN));
            } else if (state.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.OXYGEN_PANEL.get())) {
                dev.sixik.stationarenear.structures.oxygen.StationOxygenManager.onPanelPlaced((ServerLevel) level, pos, state.getValue(BROKEN));
            }
        }
    }

    public static void performRepair(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WallMountedPanelBlock && state.getValue(BROKEN)) {
            level.setBlock(pos, state.setValue(BROKEN, false), 3);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            ItemStack heldItem = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer && !serverPlayer.getAbilities().instabuild) {
                heldItem.hurtAndBreak(1, serverPlayer, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
            if (state.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.GRAVITATION_PANEL.get())) {
                dev.sixik.stationarenear.structures.gravity.StationGravitationManager.onPanelRepaired(level, pos, player);
            } else if (state.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.OXYGEN_PANEL.get())) {
                dev.sixik.stationarenear.structures.oxygen.StationOxygenManager.onPanelRepaired(level, pos, player);
            }
            player.displayClientMessage(Component.literal("Панель успешно починена и синхронизирована."), true);
        }
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
            case EAST -> eastShape;
            case SOUTH -> southShape;
            case WEST -> westShape;
            default -> northShape;
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
        builder.add(FACING, BROKEN);
    }
}
