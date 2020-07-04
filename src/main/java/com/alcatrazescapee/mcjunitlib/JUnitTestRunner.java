package com.alcatrazescapee.mcjunitlib;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.*;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public class JUnitTestRunner implements TestExecutionListener
{
    public static void register(Class<?> testClass)
    {
        TEST_CLASSES.add(testClass);
    }

    private static final List<Class<?>> TEST_CLASSES = new ArrayList<>();

    private static final Level UNIT_TEST = Level.forName("UNITTEST", 50);
    private static final Logger LOGGER = LogManager.getLogger();

    public void runAllTests()
    {
        LOGGER.log(UNIT_TEST, "Asking for Unit Test Classes...");

        try
        {
            Class.forName("com.alcatrazescapee.suckeggs.SuckEggsTest").getMethod("main").invoke(null);
        }
        catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e)
        {
            LOGGER.log(UNIT_TEST, "Unable to ask for tests");
            LOGGER.log(UNIT_TEST, e);
        }

        LOGGER.log(UNIT_TEST, "Running Unit Tests...");

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(TEST_CLASSES.stream().map(DiscoverySelectors::selectClass).toArray(DiscoverySelector[]::new))
            .build();
        Launcher launcher = LauncherFactory.create();
        TestPlan testPlan = launcher.discover(request);

        if (testPlan.containsTests())
        {
            SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
            launcher.execute(request, this, summaryListener);
            TestExecutionSummary summary = summaryListener.getSummary();
            long timeMillis = summary.getTimeFinished() - summary.getTimeStarted();

            LOGGER.log(UNIT_TEST, "Summary");
            LOGGER.log(UNIT_TEST, "Found {} Tests: Passed / Failed = {} / {}, Aborted = {}, Skipped = {}", summary.getTestsFoundCount(), summary.getTestsSucceededCount(), summary.getTestsFailedCount(), summary.getTestsAbortedCount(), summary.getTestsSkippedCount());
            LOGGER.log(UNIT_TEST, "Finished Execution in {} s ({} ms)", timeMillis / 1000, timeMillis);

            if (!summary.getFailures().isEmpty())
            {
                LOGGER.log(UNIT_TEST, "Failures:");
                for (TestExecutionSummary.Failure failure : summary.getFailures())
                {
                    LOGGER.log(UNIT_TEST, "Name: {}", failure.getTestIdentifier().getDisplayName());
                    LOGGER.log(UNIT_TEST, "Reason", failure.getException());
                }
            }
        }
        else
        {
            LOGGER.log(UNIT_TEST, "Did not discover any tests to run.");
        }
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan)
    {
        LOGGER.log(UNIT_TEST, "----------------------------");
        LOGGER.log(UNIT_TEST, "Starting Test Plan Execution with {} tests", testPlan.countTestIdentifiers(x -> true));
        LOGGER.log(UNIT_TEST, "----------------------------");
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan)
    {
        LOGGER.log(UNIT_TEST, "----------------------------");
        LOGGER.log(UNIT_TEST, "Finished Test Plan Execution");
        LOGGER.log(UNIT_TEST, "----------------------------");
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier)
    {
        LOGGER.log(UNIT_TEST, "Registered Dynamic Test: {}", testIdentifier.getDisplayName());
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason)
    {
        LOGGER.log(UNIT_TEST, "Skipped test {} due to {}", testIdentifier.getDisplayName(), reason);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier)
    {
        LOGGER.log(UNIT_TEST, "Running test {}", testIdentifier.getDisplayName());
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult)
    {
        LOGGER.log(UNIT_TEST, "Finished running test {}", testIdentifier);
    }
}
