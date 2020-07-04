# Minecraft JUnit Testing Library

This is a library designed to make it possible to test Minecraft Forge mods using JUnit tests without fearing for classloading errors caused by referencing Minecraft source code. This is much more useful than simply being able to test mod code - this can allow mod authors to interact with a world and build useful integration tests as well as unit tests involving core Minecraft classes.

### How to Use:

This assumes you already have a basic `build.gradle` mod workspace set up.

First, you must add this library a test dependency. Make sure to replace `MINECRAFT_VERSION` and `VERSION` with the version you want to use (e.g. `1.15.2` and `0.1.19`):

```groovy
// At the moment I have not setup a public maven for this. So the next best thing is to clone this project and run publishToMavenLocal, include mavenLocal() here, and it will work. todo: maven
repositories {
    mavenLocal()
}

dependencies {
    testImplementation fg.deobf('com.alcatrazescapee.mcjunitlib:mc-junit-lib-MINECRAFT_VERSION:VERSION') {
        transitive = false
    }
}
```

A couple things to note:

- This mod will package the JUnit 5 API as part of the mod jar. This is important - do not add a dependency on JUnit manually as Forge will only load mod classes using the transforming class loader which is required in order to access minecraft source code without everything crashing and burning
- `transitive = false` is to stop extra dependencies (such as forge, or JUnit dependencies) from leaking onto the classpath as they are included in the jar itself, or should not be included as they are already present in the mod dev workspace.

Then, you need to add a run configuration which is responsible for running the JUnit tests. In `minecraft`, under `runs` (where `server` and `client` typically are), add the following run configuration. Make sure to replace all references to `modid` with your mod id.

```groovy
serverTest {
    parent server // This run config inherits settings from the server config
    workingDirectory project.file('run')
    main 'com.alcatrazescapee.mcjunitlib.DedicatedTestServerLauncher' // The main class which launches a customized server which then runs JUnit tests
    ideaModule "${project.name}.test" // Tell IDEA to use the classpath of the test module
    property 'forge.logging.console.level', 'unittest' // This logging level prevents any other server information messages and leaves only the unit test output
    environment 'MOD_CLASSES', String.join(File.pathSeparator,
            "modid%%${sourceSets.main.output.resourcesDir}",
            "modid%%${sourceSets.main.output.classesDir}",
            "modid%%${sourceSets.test.output.resourcesDir}",
            "modid%%${sourceSets.test.output.classesDir}",
    ) // Forge will ignore all test sources unless we explicitly tell it to include them as mod sources
    environment 'target', 'fmltestserver' // This is a custom service used to launch with ModLauncher's transforming class loader
    mods {
        modid { // The mod that is being tested
            sources sourceSets.main
        }
    }
}
```

Finally, run `genIntellijRuns` (or equivalent for your IDE) and the `runServerTest` will be generated.


### Writing Tests

Tests should be placed in the `src/test/java` module. Resources can be placed in `src/test/resources`. Tests are standard JUnit 5 tests, for more information consult their documentation. Below is an example test class:

```java
package example;

import net.minecraft.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExampleTest
{
    @Test
    void testStoneRegistryName()
    {
        assertEquals("minecraft:stone", Blocks.STONE.getRegistryName().toString());
    }
}
```

This is the log output produced by `runServerTest` with the above test class:

```
[Server thread/UNITTEST] [mcjunitlib/]: --------------------------------------------------
[Server thread/UNITTEST] [mcjunitlib/]: Running Test Plan with 1 test(s)
[Server thread/UNITTEST] [mcjunitlib/]: Running Container JUnit Jupiter
[Server thread/UNITTEST] [mcjunitlib/]: Running Class example.ExampleTest
[Server thread/UNITTEST] [mcjunitlib/]: Running Method example.ExampleTest#testStoneRegistryName()
[Server thread/UNITTEST] [mcjunitlib/]: Finished Method example.ExampleTest#testStoneRegistryName()
[Server thread/UNITTEST] [mcjunitlib/]: Finished Class example.ExampleTest
[Server thread/UNITTEST] [mcjunitlib/]: Finished Container JUnit Jupiter
[Server thread/UNITTEST] [mcjunitlib/]: Finished Test Plan
[Server thread/UNITTEST] [mcjunitlib/]: --------------------------------------------------
[Server thread/UNITTEST] [mcjunitlib/]: Summary
[Server thread/UNITTEST] [mcjunitlib/]: Found 1 Tests
[Server thread/UNITTEST] [mcjunitlib/]:  - 1 / 1 Passed (100%)
[Server thread/UNITTEST] [mcjunitlib/]: Finished Execution in < 1 s (133 ms)
```
