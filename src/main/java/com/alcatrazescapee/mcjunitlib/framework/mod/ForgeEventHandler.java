package com.alcatrazescapee.mcjunitlib.framework.mod;

import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.alcatrazescapee.mcjunitlib.framework.IntegrationTestManager;

/**
 * This is part of the mod features of
 */
public final class ForgeEventHandler
{
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event)
    {
        IntegrationTestCommands.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event)
    {
        final World world = event.world;
        if (event.phase == TickEvent.Phase.END && world instanceof ServerWorld && event.world.dimension() == World.OVERWORLD)
        {
            IntegrationTestManager.INSTANCE.tick((ServerWorld) world);
        }
    }
}
