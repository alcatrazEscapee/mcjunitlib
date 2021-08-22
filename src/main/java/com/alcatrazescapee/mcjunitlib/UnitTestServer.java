package com.alcatrazescapee.mcjunitlib;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.ServerResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.LoggingListener;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public class UnitTestServer extends GameTestServer
{
    private static final Logger LOGGER = LogManager.getLogger("UnitTests");
    private static final String HR = "--------------------------------------------------";

    private int testsFailed;

    public UnitTestServer(Thread serverThread, LevelStorageSource.LevelStorageAccess levelStorage, PackRepository packRepo, ServerResources serverResources, RegistryAccess.RegistryHolder registries)
    {
        super(serverThread, levelStorage, packRepo, serverResources, Collections.emptyList(), new BlockPos(0, 63, 0), registries);
    }

    @Override
    public void tickServer(@Nonnull BooleanSupplier timeLeft)
    {
        // See FMLCommonLaunchHandler#processModClassesEnvironmentVariable
        String modClasses = Optional.ofNullable(System.getenv("MOD_CLASSES")).orElse("");
        LOGGER.debug("Got mod coordinates {} from env", modClasses);
        Set<Path> modClassPaths = Arrays.stream(modClasses.split(File.pathSeparator))
            .map(inp -> {
                String[] splitString = inp.split("%%", 2);
                return Paths.get(splitString[splitString.length - 1]);
            })
            .collect(Collectors.toSet());
        LOGGER.debug("Found supplied mod coordinates [{}]", modClassPaths);

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClasspathRoots(modClassPaths))
            .build();
        Launcher launcher = LauncherFactory.create();
        TestPlan testPlan = launcher.discover(request);

        if (testPlan.containsTests())
        {
            LoggingListener loggingListener = LoggingListener.forBiConsumer((error, msg) -> {
                if (error != null)
                {
                    LOGGER.error(msg.get(), error);
                }
                else
                {
                    LOGGER.info(msg.get());
                }
            });
            SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
            launcher.execute(request, summaryListener, loggingListener);
            TestExecutionSummary summary = summaryListener.getSummary();
            long timeMillis = summary.getTimeFinished() - summary.getTimeStarted();

            LOGGER.info(HR);
            LOGGER.info("Summary");
            LOGGER.info("Found {} Tests", summary.getTestsFoundCount());
            LOGGER.info(" - {} / {} Passed ({})", summary.getTestsSucceededCount(), summary.getTestsFoundCount(), String.format("%02d%%", (int) (100f * summary.getTestsSucceededCount() / summary.getTestsFoundCount())));
            if (summary.getTestsFailedCount() > 0)
            {
                testsFailed = (int) summary.getTestsFailedCount();
                LOGGER.info(" - {} / {} Failed ({})", summary.getTestsFailedCount(), summary.getTestsFoundCount(), String.format("%02d%%", (int) (100f * summary.getTestsFailedCount() / summary.getTestsFoundCount())));
            }
            if (summary.getTestsSkippedCount() > 0)
            {
                LOGGER.info(" - {} / {} Skipped ({})", summary.getTestsSkippedCount(), summary.getTestsFoundCount(), String.format("%02d%%", (int) (100f * summary.getTestsSkippedCount() / summary.getTestsFoundCount())));
            }
            if (summary.getTestsAbortedCount() > 0)
            {
                LOGGER.info(" - {} / {} Aborted ({})", summary.getTestsAbortedCount(), summary.getTestsFoundCount(), String.format("%02d%%", (int) (100f * summary.getTestsAbortedCount() / summary.getTestsFoundCount())));
            }
            String millis = timeMillis == 0 ? "< 1" : String.valueOf(timeMillis);
            String seconds = timeMillis < 1000 ? "< 1" : String.valueOf(timeMillis / 1000);
            LOGGER.info("Finished Execution in {} s ({} ms)", seconds, millis);
        }
        else
        {
            LOGGER.info("Did not discover any tests to run.");
        }
        halt(false);
    }

    @Override
    public void onServerExit()
    {
        System.exit(testsFailed);
    }
}
