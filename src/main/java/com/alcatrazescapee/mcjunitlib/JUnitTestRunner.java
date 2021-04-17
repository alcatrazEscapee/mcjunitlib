/*
 * Part of MCJUnitLib by AlcatrazEscapee
 * Work under Copyright. See the project LICENSE.md for details.
 */

package com.alcatrazescapee.mcjunitlib;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.support.descriptor.PackageSource;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public class JUnitTestRunner implements TestExecutionListener
{
    private static final Level UNIT_TEST = Level.forName("UNITTEST", 50);
    private static final Logger LOGGER = LogManager.getLogger("UnitTests");
    private static final String HR = "--------------------------------------------------";

    private int failedTests;

    public void runAllTests()
    {
        failedTests = 0;

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
            SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
            launcher.execute(request, this, summaryListener);
            TestExecutionSummary summary = summaryListener.getSummary();
            long timeMillis = summary.getTimeFinished() - summary.getTimeStarted();

            if (!summary.getFailures().isEmpty())
            {
                LOGGER.log(UNIT_TEST, HR);
                LOGGER.log(UNIT_TEST, "Failures:");
                for (TestExecutionSummary.Failure failure : summary.getFailures())
                {
                    LOGGER.log(UNIT_TEST, "Test {} Failed: {}", failure.getTestIdentifier().getDisplayName(), failure.getException().getMessage());
                    LOGGER.log(UNIT_TEST, "Failure Reason:", failure.getException());
                }
            }

            LOGGER.log(UNIT_TEST, HR);
            LOGGER.log(UNIT_TEST, "Summary");
            LOGGER.log(UNIT_TEST, "Found {} Tests", summary.getTestsFoundCount());
            LOGGER.log(UNIT_TEST, " - {} / {} Passed ({})", summary.getTestsSucceededCount(), summary.getTestsFoundCount(), String.format("%02d%%", (int) (100f * summary.getTestsSucceededCount() / summary.getTestsFoundCount())));
            if (summary.getTestsFailedCount() > 0)
            {
                LOGGER.log(UNIT_TEST, " - {} / {} Failed ({})", summary.getTestsFailedCount(), summary.getTestsFoundCount(), String.format("%02d%%", (int) (100f * summary.getTestsFailedCount() / summary.getTestsFoundCount())));
            }
            if (summary.getTestsSkippedCount() > 0)
            {
                LOGGER.log(UNIT_TEST, " - {} / {} Skipped ({})", summary.getTestsSkippedCount(), summary.getTestsFoundCount(), String.format("%02d%%", (int) (100f * summary.getTestsSkippedCount() / summary.getTestsFoundCount())));
            }
            if (summary.getTestsAbortedCount() > 0)
            {
                LOGGER.log(UNIT_TEST, " - {} / {} Aborted ({})", summary.getTestsAbortedCount(), summary.getTestsFoundCount(), String.format("%02d%%", (int) (100f * summary.getTestsAbortedCount() / summary.getTestsFoundCount())));
            }
            String millis = timeMillis == 0 ? "< 1" : String.valueOf(timeMillis);
            String seconds = timeMillis < 1000 ? "< 1" : String.valueOf(timeMillis / 1000);
            LOGGER.log(UNIT_TEST, "Finished Execution in {} s ({} ms)", seconds, millis);

            failedTests = (int) summary.getTestsFailedCount();
        }
        else
        {
            LOGGER.log(UNIT_TEST, "Did not discover any tests to run.");
        }
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan)
    {
        LOGGER.log(UNIT_TEST, HR);
        LOGGER.log(UNIT_TEST, "Running Test Plan with {} test(s)", testPlan.countTestIdentifiers(TestIdentifier::isTest));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan)
    {
        LOGGER.log(UNIT_TEST, "Finished Test Plan");
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier)
    {
        LOGGER.log(UNIT_TEST, "Registered: {}", getDisplayName(testIdentifier));
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason)
    {
        LOGGER.log(UNIT_TEST, "Skipped {} due to {}", getDisplayName(testIdentifier), reason);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier)
    {
        LOGGER.log(UNIT_TEST, "Running {}", getDisplayName(testIdentifier));
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult)
    {
        LOGGER.log(UNIT_TEST, "Finished {}", getDisplayName(testIdentifier));
    }

    public boolean hasFailedTests()
    {
        return failedTests > 0;
    }

    private String getDisplayName(TestIdentifier testIdentifier)
    {
        TestSource source = testIdentifier.getSource().orElse(null);
        if (source instanceof ClassSource)
        {
            return "Class " + ((ClassSource) source).getClassName();
        }
        else if (source instanceof MethodSource)
        {
            MethodSource method = (MethodSource) source;
            return "Method " + method.getClassName() + "#" + method.getMethodName() + "(" + method.getMethodParameterTypes() + ")";
        }
        else if (source instanceof PackageSource)
        {
            return "Package " + ((PackageSource) source).getPackageName();
        }
        else if (testIdentifier.isContainer())
        {
            return "Container " + testIdentifier.getDisplayName();
        }
        else if (testIdentifier.isTest())
        {
            return "Test " + testIdentifier.getDisplayName();
        }
        else
        {
            return testIdentifier.getDisplayName();
        }
    }
}
