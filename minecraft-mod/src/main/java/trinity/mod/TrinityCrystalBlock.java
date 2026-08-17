package trinity.mod;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Trinity Crystal — the light-curtain made visible.
 *
 * Every 2 ticks the block re-reads the tide clock (server time) and pulses its
 * light level with the heartbeat scaled by the season amplitude (luminance
 * 5..14); at breath peaks it sheds particles. The block entity renders the
 * animated glow client-side (per-crystal phase, seasonal color).
 */
public final class TrinityCrystalBlock extends Block implements BlockEntityProvider {

    public static final IntProperty PULSE = IntProperty.of("pulse", 0, 7);

    public TrinityCrystalBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState().with(PULSE, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(PULSE);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        double t = TideClock.tServer();
        int p = (int) Math.round(TideClock.heartbeat(t) * 7 * TideClock.seasonAmplitude(t));
        if (p != state.get(PULSE)) {
            world.setBlockState(pos, state.with(PULSE, p), 2);
        }
        if (TideClock.breath(t) > 0.95) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                    2, 0.3, 0.3, 0.3, 0.02);
        }
        world.scheduleBlockTick(pos, this, 2);
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient) {
            world.scheduleBlockTick(pos, this, 2);
        }
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TideCrystalBlockEntity(pos, state);
    }
}
