/*
 * Part of MCJUnitLib by AlcatrazEscapee
 * Work under Copyright. See the project LICENSE.md for details.
 */

package com.alcatrazescapee.mcjunitlib;

import java.util.concurrent.Callable;

import net.minecraftforge.fml.loading.targets.FMLServerUserdevLaunchHandler;

public class TestLaunchHandler extends FMLServerUserdevLaunchHandler
{
    @Override
    public String name()
    {
        return "mcjunitlib";
    }

    @Override
    public Callable<Void> launchService(String[] arguments, ModuleLayer layer)
    {
        return () -> {
            final String[] args = preLaunch(arguments, layer);
            Class.forName(layer.findModule("minecraft").orElseThrow(),"com.alcatrazescapee.mcjunitlib.TestMain").getMethod("main", String[].class).invoke(null, (Object) args);
            return null;
        };
    }
}
