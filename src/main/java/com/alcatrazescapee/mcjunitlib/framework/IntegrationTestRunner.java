package com.alcatrazescapee.mcjunitlib.framework;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.util.ResourceLocation;

class IntegrationTestRunner
{
    private static final Logger LOGGER = LogManager.getLogger();

    private final Class<?> clazz;
    private final Method method;
    private final Object instance;
    private final ResourceLocation templateName;

    private final int refreshTicks;
    private final int timeoutTicks;

    IntegrationTestRunner(String modId, Class<?> clazz, Method method, IntegrationTest methodAnnotation, @Nullable Object instance)
    {
        this.clazz = clazz;
        this.method = method;
        this.instance = instance;
        this.refreshTicks = methodAnnotation.refreshTicks();
        this.timeoutTicks = methodAnnotation.timeoutTicks();

        IntegrationTestClass classAnnotation = clazz.getDeclaredAnnotation(IntegrationTestClass.class);
        String className = classAnnotation != null ? classAnnotation.value() : clazz.getName();
        String testName = !"".equals(methodAnnotation.value()) ? methodAnnotation.value() : method.getName();

        this.templateName = new ResourceLocation(modId, (className + '/' + testName).toLowerCase());

    }

    String getFullName()
    {
        return clazz.getName() + "." + method.getName() + "()";
    }

    ResourceLocation getTemplateName()
    {
        return templateName;
    }

    String getClassName()
    {
        return clazz.getName();
    }

    int getTimeoutTicks()
    {
        return timeoutTicks;
    }

    int getRefreshTicks()
    {
        return refreshTicks;
    }

    void run(IntegrationTestHelper helper)
    {
        try
        {
            method.invoke(instance, helper);
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            LOGGER.warn("Unable to resolve integration test at {} (Cannot Invoke Method - {})", getFullName(), e.getMessage());
            LOGGER.debug("Error", e);
            helper.fail("Reflection Error: " + e.getMessage());
        }
    }
}
