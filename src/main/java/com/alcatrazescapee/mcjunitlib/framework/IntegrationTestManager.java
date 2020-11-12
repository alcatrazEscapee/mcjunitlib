package com.alcatrazescapee.mcjunitlib.framework;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LecternBlock;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.tileentity.LecternTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import com.alcatrazescapee.mcjunitlib.framework.mod.ForgeEventHandler;
import org.objectweb.asm.Type;

/**
 * Main handler for integration tests
 *
 * As a modder, all you should do is call {@link IntegrationTestManager#setup(String)} with your mod id.
 * The rest will be handled for you.
 */
public enum IntegrationTestManager
{
    INSTANCE;

    private static final Type INTEGRATION_TEST = Type.getType(IntegrationTest.class);
    private static final Logger LOGGER = LogManager.getLogger("IntegrationTests");

    /**
     * The entry point for unit tests.
     * This must be called from outside code, during {@link net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent}
     *
     * @param modId The mod id in question
     */
    @SuppressWarnings("unused")
    public static void setup(String modId)
    {
        ModList.get().getAllScanData().stream()
            .map(ModFileScanData::getAnnotations)
            .flatMap(Collection::stream)
            .filter(a -> a.getAnnotationType().equals(INTEGRATION_TEST))
            .map(annotation -> createIntegrationTest(modId, annotation))
            .filter(Objects::nonNull)
            .forEach(IntegrationTestManager.INSTANCE::add);

        MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());
    }

    @Nullable
    private static IntegrationTestRunner createIntegrationTest(String modId, ModFileScanData.AnnotationData annotation)
    {
        final String targetClass = annotation.getClassType().getClassName();
        final String targetName = annotation.getMemberName();
        final String targetDescriptor = "(Lcom/alcatrazescapee/mcjunitlib/framework/IntegrationTestHelper;)V";

        if (!targetName.endsWith(targetDescriptor))
        {
            LOGGER.error("Unable to resolve integration test at {}.{} (Invalid Method Signature - Must take a parameter of type IntegrationTestHelper and return void)", targetClass, targetName);
            return null;
        }

        final String targetMethodName = targetName.substring(0, targetName.length() - targetDescriptor.length());

        try
        {
            final Class<?> clazz = Class.forName(targetClass);
            final Method method = clazz.getDeclaredMethod(targetMethodName, IntegrationTestHelper.class);

            method.setAccessible(true);

            final Object instance = ((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC) ? null : clazz.newInstance();
            final IntegrationTest typedAnnotation = method.getDeclaredAnnotation(IntegrationTest.class);
            return new IntegrationTestRunner(modId, clazz, method, typedAnnotation, instance);
        }
        catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException e)
        {
            LOGGER.error("Unable to resolve integration test at {}.{} (Unknown Exception - {})", targetClass, targetName, e.getMessage());
            LOGGER.debug("Error", e);
        }
        return null;
    }

    private final HashMap<String, List<IntegrationTestRunner>> sortedTests;
    private final List<IntegrationTestRunner> allTests;
    private final List<IntegrationTestHelper> activeTests;

    private int passedTests, failedTests;
    private Status status;

    IntegrationTestManager()
    {
        this.allTests = new ArrayList<>();
        this.sortedTests = new HashMap<>();
        this.activeTests = new ArrayList<>();
        this.passedTests = 0;
        this.failedTests = 0;
        this.status = Status.WAITING;
    }

    public boolean verifyAllTests(ServerWorld world, CommandSource source)
    {
        if (status == Status.WAITING)
        {
            final TemplateManager manager = world.getStructureManager();
            boolean allPassed = true;
            for (IntegrationTestRunner test : allTests)
            {
                if (manager.get(test.getTemplateName()) == null)
                {
                    source.sendFailure(new StringTextComponent("Test '").append(test.getFullName()).append("' failed verification: No template '").append(test.getTemplateName().toString()).append("' found"));
                    allPassed = false;
                }
            }
            status = Status.VERIFIED;
            return allPassed;
        }
        return true;
    }

    public boolean setupAllTests(ServerWorld world, CommandSource source)
    {
        if (status == Status.VERIFIED || status == Status.FINISHED || status == Status.SETUP)
        {
            status = Status.SETUP;

            passedTests = failedTests = 0;
            activeTests.clear();

            int testFloorY = 3;

            final TemplateManager manager = world.getStructureManager();
            final BlockPos.Mutable cursor = new BlockPos.Mutable(0, testFloorY, 0);
            final BlockPos.Mutable mutablePos = new BlockPos.Mutable();
            final Random random = new Random();
            final PlacementSettings settings = new PlacementSettings().setRandom(random);

            int maxZSize = 0;

            for (Map.Entry<String, List<IntegrationTestRunner>> entry : sortedTests.entrySet())
            {
                for (IntegrationTestRunner test : entry.getValue())
                {
                    final Template template = manager.getOrCreate(test.getTemplateName());
                    final BlockPos size = template.getSize();
                    final BlockPos testBoxOrigin = cursor.immutable();
                    final BlockPos testTemplateOrigin = testBoxOrigin.offset(1, 1, 1);

                    // Clear the test area
                    for (int x = testBoxOrigin.getX(); x <= testBoxOrigin.getX() + size.getX() + 1; x++)
                    {
                        for (int z = testBoxOrigin.getZ(); z <= testBoxOrigin.getZ() + size.getZ() + 1; z++)
                        {
                            mutablePos.set(x, testFloorY, z);

                            // Build a floor with a fancy construction-tape border
                            if (x == testBoxOrigin.getX() || x == testBoxOrigin.getX() + size.getX() + 1 || z == testBoxOrigin.getZ() || z == testBoxOrigin.getZ() + size.getZ() + 1)
                            {
                                // Border
                                world.setBlockAndUpdate(mutablePos, ((x + z) & 1) == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
                            }
                            else
                            {
                                world.setBlockAndUpdate(mutablePos, Blocks.GRAY_CONCRETE.defaultBlockState());
                            }

                            // Clear the area of the test
                            for (int y = testTemplateOrigin.getY(); y <= testTemplateOrigin.getY() + size.getY() + 1; y++)
                            {
                                mutablePos.set(x, y, z);
                                world.setBlockAndUpdate(mutablePos, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }

                    // Build the indicator beacon
                    for (int x = testBoxOrigin.getX() - 1; x <= testBoxOrigin.getX() + 1; x++)
                    {
                        for (int z = testBoxOrigin.getZ() - 1; z <= testBoxOrigin.getZ() + 1; z++)
                        {
                            mutablePos.set(x, testFloorY - 2, z);
                            world.setBlockAndUpdate(mutablePos, Blocks.IRON_BLOCK.defaultBlockState());
                        }
                    }
                    world.setBlockAndUpdate(mutablePos.setWithOffset(testBoxOrigin, Direction.DOWN), Blocks.BEACON.defaultBlockState());
                    world.setBlockAndUpdate(mutablePos.set(testBoxOrigin), Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());

                    // Add the lectern with log book
                    world.setBlockAndUpdate(mutablePos.setWithOffset(testBoxOrigin, -1, 1, -1), Blocks.LECTERN.defaultBlockState());
                    ItemStack book = new ItemStack(Items.WRITABLE_BOOK, 1);
                    editLogBook(book, test.getFullName(), "Setup", Collections.emptyList());
                    LecternBlock.tryPlaceBook(world, mutablePos, world.getBlockState(mutablePos), new ItemStack(Items.WRITABLE_BOOK));

                    // Build the test itself
                    template.placeInWorld(world, testTemplateOrigin, settings, random);

                    // Move the cursor
                    cursor.move(Direction.EAST, size.getX() + 2 + 3); // +x
                    maxZSize = Math.max(maxZSize, size.getZ());

                    // Begin test
                    final IntegrationTestHelper helper = new IntegrationTestHelper(world, test, testTemplateOrigin, size);
                    activeTests.add(helper);
                }

                // Move the cursor to the next row
                cursor.setX(0);
                cursor.move(Direction.SOUTH, maxZSize + 2 + 3); // +z
            }

            source.sendSuccess(new StringTextComponent("Setup Finished!"), true);
            return true;
        }
        return false;
    }

    public boolean runAllTests(ServerWorld world)
    {
        if (status == Status.SETUP)
        {
            for (IntegrationTestHelper activeTest : activeTests)
            {
                // Run tests and setup conditions
                activeTest.getTest().run(activeTest);

                // Update the log book
                TileEntity te = world.getBlockEntity(activeTest.getOrigin().offset(-2, 0, -2));
                if (te instanceof LecternTileEntity)
                {
                    editLogBook(((LecternTileEntity) te).getBook(), activeTest.getTest().getFullName(), "Running", Collections.emptyList());
                }
            }
            status = Status.RUNNING;
            return true;
        }
        return false;
    }

    public void tick(ServerWorld world)
    {
        if (!activeTests.isEmpty() && status == Status.RUNNING)
        {
            Iterator<IntegrationTestHelper> iterator = activeTests.iterator();
            while (iterator.hasNext())
            {
                IntegrationTestHelper helper = iterator.next();
                helper.tick().ifPresent(result -> {
                    BlockState glass;
                    if (result.isSuccess())
                    {
                        glass = Blocks.GREEN_STAINED_GLASS.defaultBlockState();
                        passedTests++;
                    }
                    else
                    {
                        glass = Blocks.RED_STAINED_GLASS.defaultBlockState();
                        failedTests++;

                        // Send failure messages!
                        if (!result.getErrors().isEmpty())
                        {
                            LOGGER.error("Test Failed {}", helper.getTest().getFullName());
                            for (String error : result.getErrors())
                            {
                                LOGGER.error(error);
                            }
                        }
                    }

                    // Update the beacon state
                    world.setBlockAndUpdate(helper.getOrigin().offset(-1, -1, -1), glass);

                    // Update the log book
                    TileEntity te = world.getBlockEntity(helper.getOrigin().offset(-2, 0, -2));
                    if (te instanceof LecternTileEntity && ((LecternTileEntity) te).hasBook())
                    {
                        String status = result.isSuccess() ? "Pass" : "Fail";
                        editLogBook(((LecternTileEntity) te).getBook(), helper.getTest().getFullName(), status, result.getErrors());
                    }

                    iterator.remove();
                });
            }

            if (activeTests.isEmpty())
            {
                int totalTests = passedTests + failedTests;
                LOGGER.info("Integration Testing Complete!");
                LOGGER.info("Passed: {} / {} ({} %)", passedTests, totalTests, String.format("%.1f", 100f * passedTests / totalTests));
                LOGGER.info("Failed: {} / {} ({} %)", failedTests, totalTests, String.format("%.1f", 100f * failedTests / totalTests));

                status = Status.FINISHED;
            }
        }
    }

    void add(IntegrationTestRunner test)
    {
        allTests.add(test);
        sortedTests.computeIfAbsent(test.getClassName(), key -> new ArrayList<>()).add(test);
    }

    private void editLogBook(ItemStack stack, String testName, String status, List<String> errors)
    {
        CompoundNBT bookNbt = new CompoundNBT();
        ListNBT pagesNbt = new ListNBT();
        StringBuilder builder = new StringBuilder().append("Test: ").append(testName).append("\nStatus: ").append(status);
        if (!errors.isEmpty())
        {
            builder.append("\nErrors: ");
            for (String error : errors)
            {
                builder.append(error);
            }
        }
        while (builder.length() > 250)
        {
            pagesNbt.add(StringNBT.valueOf(builder.substring(0, 250) + "..."));
            builder.delete(0, 250);
        }
        pagesNbt.add(StringNBT.valueOf(builder.toString()));
        bookNbt.put("pages", pagesNbt);
        stack.setTag(bookNbt);
    }

    private enum Status
    {
        WAITING,
        VERIFIED,
        SETUP,
        RUNNING,
        FINISHED
    }
}
