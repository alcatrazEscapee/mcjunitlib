package com.alcatrazescapee.mcjunitlib.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import net.minecraft.block.*;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.tags.ITag;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.world.server.ServerWorld;

/**
 * @see IntegrationTest
 * @see IntegrationTestClass
 */
public class IntegrationTestHelper
{
    private final ServerWorld world;
    private final IntegrationTestRunner test;
    private final BlockPos origin;
    private final MutableBoundingBox boundingBox;

    private final List<Supplier<String>> assertions;

    private int currentTick;

    public IntegrationTestHelper(ServerWorld world, IntegrationTestRunner test, BlockPos origin, BlockPos size)
    {
        this.world = world;
        this.test = test;
        this.origin = origin;
        this.boundingBox = new MutableBoundingBox(BlockPos.ZERO, size.offset(1, 1, 1));

        this.assertions = new ArrayList<>();
        this.currentTick = 0;
    }

    public void destroyBlock(BlockPos pos)
    {
        destroyBlock(pos, false);
    }

    public void destroyBlock(BlockPos pos, boolean dropBlock)
    {
        relativePos(pos).ifPresent(actualPos -> world.destroyBlock(actualPos, dropBlock));
    }

    public void setBlockState(BlockPos pos, BlockState state)
    {
        relativePos(pos).ifPresent(actualPos -> world.setBlockAndUpdate(actualPos, state));
    }

    public void pushButton(BlockPos pos)
    {
        relativePos(pos).ifPresent(actualPos -> {
            BlockState state = world.getBlockState(actualPos);
            if (state.getBlock() instanceof AbstractButtonBlock)
            {
                ((AbstractButtonBlock) state.getBlock()).press(state, world, actualPos);
            }
        });
    }

    public void pullLever(BlockPos pos)
    {
        relativePos(pos).ifPresent(actualPos -> {
            BlockState state = world.getBlockState(actualPos);
            if (state.getBlock() instanceof LeverBlock)
            {
                ((LeverBlock) state.getBlock()).pull(state, world, actualPos);
            }
        });
    }

    public BlockState getBlockState(BlockPos pos)
    {
        return relativePos(pos).map(world::getBlockState).orElseGet(Blocks.AIR::defaultBlockState);
    }

    public FluidState getFluidState(BlockPos pos)
    {
        return relativePos(pos).map(world::getFluidState).orElseGet(Fluids.EMPTY::defaultFluidState);
    }

    @Nullable
    public TileEntity getTileEntity(BlockPos pos)
    {
        return relativePos(pos).map(world::getBlockEntity).orElse(null);
    }

    public void assertAirAt(BlockPos pos, String message)
    {
        assertBlockAt(pos, Blocks.AIR, message);
    }

    public void assertBlockAt(BlockPos pos, Block block, String message)
    {
        assertBlockAt(pos, stateIn -> stateIn.is(block), message);
    }

    public void assertBlockAt(BlockPos pos, ITag<Block> tag, String message)
    {
        assertBlockAt(pos, stateIn -> stateIn.is(tag), message);
    }

    public void assertBlockAt(BlockPos pos, BlockState state, String message)
    {
        assertBlockAt(pos, stateIn -> state == stateIn, message);
    }

    public void assertBlockAt(BlockPos pos, Predicate<BlockState> condition, String message)
    {
        relativePos(pos).ifPresent(actualPos -> assertTrue(() -> condition.test(world.getBlockState(actualPos)), message));
    }

    public void assertFluidAt(BlockPos pos, Fluid fluid, String message)
    {
        assertFluidAt(pos, stateIn -> stateIn.getType() == fluid, message);
    }

    public void assertFluidAt(BlockPos pos, FluidState fluidState, String message)
    {
        assertFluidAt(pos, stateIn -> stateIn == fluidState, message);
    }

    public void assertFluidAt(BlockPos pos, ITag<Fluid> tag, String message)
    {
        assertFluidAt(pos, stateIn -> stateIn.is(tag), message);
    }

    public void assertFluidAt(BlockPos pos, Predicate<FluidState> condition, String message)
    {
        relativePos(pos).ifPresent(actualPos -> assertTrue(() -> condition.test(world.getFluidState(actualPos)), message));
    }

    public void assertTileEntityAt(BlockPos pos, Class<? extends TileEntity> teClazz, String message)
    {
        assertTileEntityAt(pos, te -> true, teClazz, message);
    }

    public void assertTileEntityAt(BlockPos pos, TileEntityType<?> type, String message)
    {
        assertTileEntityAt(pos, te -> te.getType() == type, message);
    }

    public void assertTileEntityAt(BlockPos pos, Predicate<TileEntity> condition, String message)
    {
        relativePos(pos).ifPresent(actualPos -> assertThat(() -> {
            TileEntity te = world.getBlockEntity(pos);
            if (te != null)
            {
                return condition.test(te) ? null : message;
            }
            else
            {
                return "There was no tile entity at " + pos;
            }
        }));
    }

    @SuppressWarnings("unchecked")
    public <T extends TileEntity> void assertTileEntityAt(BlockPos pos, Predicate<T> condition, Class<T> teClass, String message)
    {
        relativePos(pos).ifPresent(actualPos -> assertThat(() -> {
            TileEntity te = world.getBlockEntity(actualPos);
            if (te == null)
            {
                return "There was no tile entity at " + pos;
            }
            if (teClass.isInstance(te))
            {
                return condition.test((T) te) ? null : message;
            }
            else
            {
                return "Tile entity at " + pos + "is not an instance of " + teClass.getName();
            }
        }));
    }

    /**
     * @param condition A condition describing if the test passes (true) or fails (false)
     * @param message   An error message for if the test fails
     */
    public void assertTrue(BooleanSupplier condition, String message)
    {
        assertions.add(() -> condition.getAsBoolean() ? null : message);
    }

    /**
     * If the supplier returns a non null string, it is assumed to be an error message and the condition has failed.
     * If the supplier returns null, the condition has passed
     *
     * @param optionalErrorIfFail A condition
     */
    public void assertThat(Supplier<String> optionalErrorIfFail)
    {
        assertions.add(optionalErrorIfFail);
    }

    public void fail(String message)
    {
        assertions.add(() -> message);
    }

    /**
     * Gets the in-world position, based on a zero centered position within the test structure
     * Anything that references the world directly should call this to handle any positions.
     * It is considered a failure to reference a position outside of the test structure.
     *
     * @param pos A position, where the origin is the origin of the test structure.
     * @return The corresponding world position, or empty if the position was outside of the test structure.
     */
    public Optional<BlockPos> relativePos(BlockPos pos)
    {
        if (boundingBox.isInside(pos))
        {
            return Optional.of(origin.offset(pos));
        }
        fail("Tried to access the position " + pos + " which was not inside the test area!");
        return Optional.empty();
    }

    /**
     * Gets the world.
     *
     * WARNING: Be careful when using this with any positions directly.
     * Make sure to call {@link IntegrationTestHelper#relativePos(BlockPos)} for any positions interacting with the world.
     *
     * @return The world
     */
    public ServerWorld getWorld()
    {
        return world;
    }

    Optional<TestResult> tick()
    {
        currentTick++;
        if (currentTick % test.getRefreshTicks() == 0)
        {
            // Refresh conditions
            List<String> failures = new ArrayList<>();
            for (Supplier<String> assertion : assertions)
            {
                String error = assertion.get();
                if (error != null)
                {
                    failures.add(error);
                }
            }

            if (failures.isEmpty())
            {
                // Test passed!
                return Optional.of(new TestResult(Collections.emptyList(), true));
            }
            if (test.getTimeoutTicks() != -1 && currentTick >= test.getTimeoutTicks())
            {
                // Test failed due to time out
                failures.add(test.getFullName() + " Failed after time out at " + test.getTimeoutTicks() + " ticks.");
                return Optional.of(new TestResult(failures, false));
            }
        }
        return Optional.empty();
    }

    BlockPos getOrigin()
    {
        return origin;
    }

    IntegrationTestRunner getTest()
    {
        return test;
    }
}
