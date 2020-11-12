package com.alcatrazescapee.mcjunitlib.framework.mod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.alcatrazescapee.mcjunitlib.framework.IntegrationTestManager;

/**
 * This is part of the mod features of the integration tests
 * It is registered conditionally in {@link IntegrationTestManager#setup(String)}
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

    @SubscribeEvent
    public void onCreateWorldSpawn(WorldEvent.CreateSpawnPosition event)
    {
        // Spawn just beneath the test area
        event.getSettings().setSpawn(new BlockPos(0, 4, -10), 0.0F);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event)
    {
        if (event.getWorld() instanceof ServerWorld)
        {
            final ServerWorld world = (ServerWorld) event.getWorld();
            final MinecraftServer server = world.getServer();
            final GameRules rules = world.getGameRules();

            // Game rules for best test setup
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);

            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, server);
            rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, server);
            rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
        }
    }
}
