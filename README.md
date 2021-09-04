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

The latest versions can be checked by looking at the [releases](https://github.com/alcatrazEscapee/mcjunitlib/releases) page. As of time of writing (2021-04-17), the latest versions are:

- Minecraft 1.16.5: `1.4.3` (Latest)
- Minecraft 1.15.2: `1.0.1`

Note: This mod will package the JUnit 5 API as part of the mod jar. This is important - do not add a dependency on JUnit manually as Forge will only load mod classes using the transforming class loader which is required in order to access minecraft source code without everything crashing and burning.

## Adding the run configuration
Add a new run configuration to the `build.gradle` file with the following, placed inside the `minecraft { runs }` block. After adding this section, you will need to continue reading to add an environment variable for the system to locate your unit tests.

- Make sure to replace `modid` with your mod id, or use the `${mod_id}` replacement.
- The `arg '--crashOnFailedTests'` is optional, recommended for a CI environment, it will cause failed tests to crash the server and exit (as opposed to continuing to run the server, allowing a local player to connect and inspect failed tests).
- The `forceExit = false` is optional, recommended for a CI environment, when not using the IDE run configurations.

```groovy
serverTest {
    parent runs.server // This run config inherits settings from the server config
    workingDirectory project.file('run')
    main 'com.alcatrazescapee.mcjunitlib.DedicatedTestServerLauncher' // The main class which launches a customized server which then runs JUnit tests
    ideaModule "${project.name}.test" // Tell IDEA to use the classpath of the test module
    property 'forge.logging.console.level', 'unittest' // This logging level prevents any other server information messages and leaves only the test output
    environment 'target', 'fmltestserver' // This is a custom service used to launch with ModLauncher's transforming class loader
    environment 'targetModId', "${mod_id}" // Pass the mod ID directly to mcjunitlib, to find integration test classes from the mod annotation scan data
    arg '--crashOnFailedTests' // Optional. Recommended when running in an automated environment. Without it, the server will continue running (and can be connected to via localhost) to inspect why tests failed.
    forceExit = false // Optional. Recommended when running in an automated environment, or via the console rather than run configurations. This will allow the task to pass successfully when all tests pass. Use if you see errors along the lines of 'Gradle daemon disappeared unexpectedly'.
    mods {
        modid {
            sources sourceSets.main, sourceSets.test
        }
    }
}
```

### Use specific classpath directories
```groovy
def testClasspaths = String.join(File.pathSeparator,
        "${mod_id}%%${sourceSets.main.output.resourcesDir}",
        "${mod_id}%%${sourceSets.main.output.classesDirs.asPath}",
        "${mod_id}%%${sourceSets.test.output.resourcesDir}",
        "${mod_id}%%${sourceSets.test.output.classesDirs.asPath}")
```

And then, inside the `serverTest` block, add the following line:
```groovy
environment 'MOD_CLASSES', testClasspaths   // target specific classpaths
```

### Use named module classpaths
This option is similar to how Intellij and Eclipse load unit tests natively. 
```groovy
def testModules = String.join(File.pathSeparator, "${mod_id}%%${project.name}.test")
```

And then, inside the `serverTest` block, add the following line:
```groovy
environment 'MOD_MODULES', testModules   // target specific named modules
```

After editing run configuration, run `genIntellijRuns` (or equivalent for your IDE) and the `runServerTest` will be generated.


## Unit Tests

Using unit tests are fairly straightforward if you are familiar with how JUnit tests work. A server instance will be running, and you can access it via `ServerLifecycleHooks.getCurrentServer()`. It is advised that tests that would interact directly with the `World` are done as [Integration Tests](#integration-tests) instead. Unit tests should be short, simple, and focused.

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

1) First, build the test. This can be anything that is save-able using a vanilla structure block.
2) Save the structure, using the vanilla structure block. Once it is saved, move the generated `.nbt` file to your mod `src/test/resources` sources.
3) Write a test class and method. Each method must match up exactly with a structure file.

- A structure will be searched for under `<test_class>/<test_name>.nbt`.
- The `<test_class>` is either the lowercase name of the class, or the `value` field of a class annotated with `@IntegrationTestClass`, if present.
- The `<test_name>` is either the lowercase name of the test method, or the `value` field of the `@IntegrationTest` annotation, if present.

When the test server is ran, integration tests will be constructed and ran, and can be viewed by connecting to the server after tests have finished (if `--crashOnFailedTests` was not passed in).

In order to run the integration tests manually:

1) Create a new world, with the world type "Superflat", Disable Structures, Creative, and Cheats Enabled.
2) Run `/integrationTests setup`. This will build all integration tests.
3) Run `/integrationTests run`. This will run all integration tests. Success will result in green beacon beams. Failures will result in red beacon beams and errors emitted to the log.

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
