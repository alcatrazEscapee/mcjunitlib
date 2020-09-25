/*
 * Part of MCJUnitLib by AlcatrazEscapee
 * Work under Copyright. See the project LICENSE.md for details.
 */

package com.alcatrazescapee.mcjunitlib;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import net.minecraft.resources.DataPackRegistries;
import net.minecraft.resources.ResourcePackList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerPropertiesProvider;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.chunk.listener.IChunkStatusListenerFactory;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraft.world.storage.SaveFormat;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;

public class DedicatedTestServer extends DedicatedServer
{
    private static final Level UNIT_TEST = Level.forName("UNITTEST", 50);
    private static final Logger LOGGER = LogManager.getLogger();

    public DedicatedTestServer(Thread thread, DynamicRegistries.Impl dynamicRegistries, SaveFormat.LevelSave saveFormat, ResourcePackList resourcePacks, DataPackRegistries dataPacks, IServerConfiguration serverConfiguration, ServerPropertiesProvider serverProperties, DataFixer dataFixer, MinecraftSessionService service, GameProfileRepository profileRepository, PlayerProfileCache profileCache, IChunkStatusListenerFactory chunkStatusListenerFactory)
    {
        super(thread, dynamicRegistries, saveFormat, resourcePacks, dataPacks, serverConfiguration, serverProperties, dataFixer, service, profileRepository, profileCache, chunkStatusListenerFactory);
    }

    @Override
    protected void runServer()
    {
        LOGGER.log(UNIT_TEST, "Server Thread Starting");
        try
        {
            if (initServer())
            {
                ServerLifecycleHooks.handleServerStarted(this);
                new JUnitTestRunner().runAllTests();
                ServerLifecycleHooks.handleServerStopping(this);
            }
            ServerLifecycleHooks.expectServerStopped(); // has to come before finalTick to avoid race conditions
        }
        catch (Throwable throwable1)
        {
            LOGGER.log(UNIT_TEST, "Encountered an unexpected exception", throwable1);
            CrashReport crashreport;
            if (throwable1 instanceof ReportedException)
            {
                crashreport = this.fillReport(((ReportedException) throwable1).getReport());
            }
            else
            {
                crashreport = this.fillReport(new CrashReport("Exception in server tick loop", throwable1));
            }

            File file1 = new File(new File(this.getServerDirectory(), "crash-reports"), "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-server.txt");
            if (crashreport.saveToFile(file1))
            {
                LOGGER.log(UNIT_TEST, "This crash report has been saved to: {}", file1.getAbsolutePath());
            }
            else
            {
                LOGGER.log(UNIT_TEST, "We were unable to save this crash report to disk.");
            }

            ServerLifecycleHooks.expectServerStopped(); // has to come before finalTick to avoid race conditions
        }
        finally
        {
            try
            {
                ObfuscationReflectionHelper.setPrivateValue(MinecraftServer.class, this, true, "field_71316_v");
                stopServer();
            }
            catch (Throwable throwable)
            {
                LOGGER.log(UNIT_TEST, "Exception stopping the server", throwable);
            }
            finally
            {
                ServerLifecycleHooks.handleServerStopped(this);
                onServerExit();
            }
        }
    }

    @Override
    public long getMaxTickLength()
    {
        return 0; // Override to prevent server watchdog thread from starting and crashing long running tests
    }
}
