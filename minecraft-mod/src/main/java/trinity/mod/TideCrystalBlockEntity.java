package trinity.mod;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import trinity.core.Rng;

/**
 * Block entity behind the Trinity Crystal. Each crystal carries a per-position
 * phase offset, so the population does not pulse in lockstep — the cave
 * breathes with a travelling wave. The client renderer animates from the
 * client clock; the block's light pulse runs on the server clock.
 */
public final class TideCrystalBlockEntity extends BlockEntity {

    private final double phase; // seconds offset

    public TideCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(TrinityMod.CRYSTAL_ENTITY, pos, state);
        this.phase = Rng.hash3(pos.getX(), pos.getY(), pos.getZ(), 0x51DE) * 400.0;
    }

    public double phase() {
        return phase;
    }

    public double localTide() {
        return TideClock.tide(TideClock.tClient() + phase);
    }

    public double localBreath() {
        return TideClock.breath(TideClock.tClient() + phase);
    }

    public double localSeason() {
        return TideClock.tClient() + phase;
    }
}
