# Minecraft Testing Library

This is a library which attempt to make Minecraft Forge mods extensively, completely, and automatically testable! This currently allows two different kinds of testing:

1) **Unit Testing** via JUnit. This is the most basic type of automated testing. However, in a Minecraft modding context, Junit tests are difficult to execute because referencing un-initialized Minecraft source code will inevitably cause class loading errors and other problems. So this attempts to remedy that by launching JUnit tests from within the transforming class loader environment, within a running `MinecraftServer` instance. These are meant for well targeted, simple tests of complex systems.

2) **Integration Testing**. This was heavily inspired by a video on [Minecraft's Testing System](https://www.youtube.com/watch?v=vXaWOJTCYNg&feature=youtu.be). And it is essentially a recreation of the core philosophy: Tests are represented as a pair of a structure (saved using a structure block), and a test method. The method is able to declare actions (e.g. place blocks, pull levers) to initiate the test, and use a wide range of assertions (assert blocks, fluids, tile entities meet conditions) in order to characterize test success.

If you experience any issues using this library, or have a suggestion for how it could be improved, please submit an issue, and I will try to address any concerns raised. Happy testing! :)

## Usage

This assumes you already have a basic Minecraft Forge mod dev `build.gradle` mod workspace set up.

First, you must add this library a test dependency. Make sure to replace `MINECRAFT_VERSION` and `VERSION` with the version you want to use (see below for latest versions):

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    testImplementation fg.deobf('com.github.alcatrazEscapee:mcjunitlib:VERSION-MINECRAFT_VERSION')
}
```

The latest versions can be checked by looking at the [releases](https://github.com/alcatrazEscapee/mcjunitlib/releases) page. As of time of writing (2020-11-12), the latest versions are:

- Minecraft 1.16.4: `1.3.0`
- Minecraft 1.15.2: `1.0.1`

Note: This mod will package the JUnit 5 API as part of the mod jar. This is important - do not add a dependency on JUnit manually as Forge will only load mod classes using the transforming class loader which is required in order to access minecraft source code without everything crashing and burning.

Then, for [Unit Tests](#unit-tests), the following run configuration is required.

- Make sure to replace `modid` with your mod id, or use the `${mod_id}` replacement.
- This run configuration inherits from the standard `server` configuration. It will run a customized `MinecraftServer` instance.

```groovy
unitTests {
    parent runs.server // This run config inherits settings from the server config
    workingDirectory project.file('run')
    main 'com.alcatrazescapee.mcjunitlib.DedicatedTestServerLauncher' // The main class which launches a customized server which then runs JUnit tests
    ideaModule "${project.name}.test" // Tell IDEA to use the classpath of the test module
    property 'forge.logging.console.level', 'unittest' // This logging level prevents any other server information messages and leaves only the unit test output
    environment 'MOD_CLASSES', String.join(File.pathSeparator,
        "${mod_id}%%${sourceSets.main.output.resourcesDir}",
        "${mod_id}%%${sourceSets.main.output.classesDir}",
        "${mod_id}%%${sourceSets.test.output.resourcesDir}",
        "${mod_id}%%${sourceSets.test.output.classesDir}",
    ) // Forge will ignore all test sources unless we explicitly tell it to include them as mod sources
    environment 'target', 'fmltestserver' // This is a custom service used to launch with ModLauncher's transforming class loader
    mods {
        modid { // The mod that is being tested - Replace this with your mod ID!
            sources sourceSets.main
        }
    }
}
```

If [Integration Tests](#integration-tests) are desired, add the following run configuration:

- As above, Make sure to replace `modid` with your mod id, or use the `${mod_id}` replacement.
- This is **not** a fully automated configuration. It launches the Minecraft client - in order to run the integration tests, a world needs to be created, loaded, and two commands run. (See [Integration Tests](#integration-tests) for details.)

```groovy
integrationTests {
    parent runs.client // This is a client run configuration, it will launch a client.
    workingDirectory project.file('run')
    ideaModule "${project.name}.test"
    property 'forge.logging.console.level', 'debug'
    environment 'MOD_CLASSES', String.join(File.pathSeparator,
        "${mod_id}%%${sourceSets.main.output.resourcesDir}",
        "${mod_id}%%${sourceSets.main.output.classesDir}",
        "${mod_id}%%${sourceSets.test.output.resourcesDir}",
        "${mod_id}%%${sourceSets.test.output.classesDir}",
    ) // Forge will ignore all test sources unless we explicitly tell it to include them as mod sources
    mods {
        modid {
            sources sourceSets.main, sourceSets.test
        }
    }
}
```

After editing either run configuration, run `genIntellijRuns` (or equivalent for your IDE) and the `runUnitTests` or `runIntegrationTests` configuration will be generated.


## Unit Tests

Using unit tests are fairly straightforward if you are familiar with how JUnit tests work. A server instance will be running, and you can access it via `ServerLifecycleHooks.getCurrentServer()`. It is advised that tests that would interact directly with the `World` are done as [Integration Tests](#integration-tests) instead. Unit tests should be short, simple, and focused.

When `runUnitTests` is ran, the log output (by default) will *only* contain messages from unit test code.

Tests should be placed in the `src/test/java` module. Resources can be placed in `src/test/resources`. Tests are standard JUnit 5 tests, for more information consult their documentation. Below is an example test class:

```java
package example;

import net.minecraft.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExampleTest
{
    @Test
    public void testStoneRegistryName()
    {
        assertEquals("minecraft:stone", Blocks.STONE.getRegistryName().toString());
    }
}
```

This is the log output produced by `runServerTest` with the above test class:

```
[Server thread/UNITTEST] [UnitTests/]: --------------------------------------------------
[Server thread/UNITTEST] [UnitTests/]: Running Test Plan with 1 test(s)
[Server thread/UNITTEST] [UnitTests/]: Running Container JUnit Jupiter
[Server thread/UNITTEST] [UnitTests/]: Running Class example.ExampleTest
[Server thread/UNITTEST] [UnitTests/]: Running Method example.ExampleTest#testStoneRegistryName()
[Server thread/UNITTEST] [UnitTests/]: Finished Method example.ExampleTest#testStoneRegistryName()
[Server thread/UNITTEST] [UnitTests/]: Finished Class example.ExampleTest
[Server thread/UNITTEST] [UnitTests/]: Finished Container JUnit Jupiter
[Server thread/UNITTEST] [UnitTests/]: Finished Test Plan
[Server thread/UNITTEST] [UnitTests/]: --------------------------------------------------
[Server thread/UNITTEST] [UnitTests/]: Summary
[Server thread/UNITTEST] [UnitTests/]: Found 1 Tests
[Server thread/UNITTEST] [UnitTests/]:  - 1 / 1 Passed (100%)
[Server thread/UNITTEST] [UnitTests/]: Finished Execution in < 1 s (133 ms)
```

## Integration Tests

Integration tests are slightly more complex to construct, but the results are much more coverage of interacting mechanics, and in-world test cases. To create an integration test:

1) First, build the test. This can be anything that is saveable using a vanilla structure block.
2) Save the structure, using the vanilla structure block. Once it is saved, move the generated `.nbt` file to your mod `src/test/resources` sources.
3) Write a test class and method. Each method must match up exactly with a structure file.
4) Add an entry point for the integration test infrastructure into your test sources (`src/main/test`) - more on this later.
5) Create a new world, with the world type "Superflat", Disable Structures, Creative, and Cheats Enabled.
7) Run `/integrationTests setup`. This will build all integration tests.
8) Run `/integrationTests run`. This will run all integration tests. Success will result in green beacon beams. Failures will result in red beacon beams and errors emitted to the log.

You can re-run `setup` and `run` as many times as necessary, provided they execute in that order. While tests are running, they will be indicated by a gray beacon beam. Only once tests have all finished (all beacon beams are red or green) can you run the tests again.

A sample test class might look like this:

```java
package example;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import com.alcatrazescapee.mcjunitlib.framework.IntegrationTest;
import com.alcatrazescapee.mcjunitlib.framework.IntegrationTestClass;
import com.alcatrazescapee.mcjunitlib.framework.IntegrationTestHelper;

@IntegrationTestClass(value = "piston_pushing_test")
public class PistonPushingTest
{
    @IntegrationTest(value = "piston_pushes_stone")
    public void testPistonPushesStone(IntegrationTestHelper helper)
    {
        helper.pushButton(new BlockPos(3, 0, 1));
        helper.assertBlockAt(new BlockPos(1, 0, 0), Blocks.STONE, "The piston should move the stone block");
    }
}
```

There are a few important things to note here:

- In this example, there is one test method. (`PistonPushingTest#pistonPushes`). The test method is identified by the `@IntegrationTest` annotation.
- The structure that would be referenced will be the class name (or `value` on the class annotation, if it exists), plus `/`, plus the method name (or `value` on the annotation if it exists). In this case, the structure referenced would be `modid:piston_pushing_test/piston_pushes_stone`.
- A test class MAY be annotated with `@IntegrationTestClass` (It is not required, but recommended). If it is omitted, the class name will be used directly to infer structure names.
- Test methods MUST be annotated with `@IntegrationTest`.
- Test methods MUST have one parameter, of type `IntegrationTestHelper`. This is used to interact with the world directly, and characterize success and failure of the test via various `assert[Thing]` methods.

Finally, in order for integration tests to work at all, it needs to be initialized by mod code. You will need to add the following class (or something functionally equivalent) into your test sources. This will trigger mcjunitlib to register commands, edit the spawn location, and locate test classes and methods.

```java
package example;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import com.alcatrazescapee.mcjunitlib.framework.IntegrationTestManager;

@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class IntegrationTestEntryPoint
{
    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event)
    {
        IntegrationTestManager.setup(MyMod.MOD_ID);
    }
}
```