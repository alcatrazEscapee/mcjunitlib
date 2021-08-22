package com.alcatrazescapee.mcjunitlib;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.CrashReport;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerResources;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.dedicated.Settings;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;

import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.fmllegacy.server.ServerModLoader;

import joptsimple.OptionParser;

public class TestMain
{
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String TEST_WORLD = "test-world";

    public static void main(String[] args)
    {
        SharedConstants.tryDetectVersion();
        OptionParser spec = new OptionParser();

        spec.accepts("nogui");
        spec.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        spec.accepts("demo");
        spec.accepts("bonusChest");
        spec.accepts("forceUpgrade");
        spec.accepts("eraseCache");
        spec.accepts("safeMode", "Loads level with vanilla datapack only");
        spec.accepts("help").forHelp();
        spec.accepts("singleplayer").withRequiredArg();
        spec.accepts("universe").withRequiredArg().defaultsTo(".");
        spec.accepts("world").withRequiredArg();
        spec.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1);
        spec.accepts("serverId").withRequiredArg();
        spec.nonOptions();

        spec.accepts("allowUpdates").withRequiredArg().ofType(Boolean.class).defaultsTo(Boolean.TRUE); // Forge: allow mod updates to proceed
        spec.accepts("gameDir").withRequiredArg().ofType(File.class).defaultsTo(new File(".")); //Forge: Consume this argument, we use it in the launcher, and the client side.

        try
        {
            CrashReport.preload();
            Bootstrap.bootStrap();
            Bootstrap.validate();
            Util.startTimerHackThread();
            ServerModLoader.load(); // Load mods before we load almost anything else anymore. Single spot now.
            RegistryAccess.RegistryHolder builtinRegistries = RegistryAccess.builtin();

            LOGGER.info("Removing previous test world...");
            FileUtils.deleteDirectory(new File(TEST_WORLD));

            Path settingsPath = Paths.get("server.properties");
            DedicatedServerSettings settingsHolder = new DedicatedServerSettings(settingsPath);
            Properties properties = Objects.requireNonNull(ObfuscationReflectionHelper.getPrivateValue(Settings.class, settingsHolder.getProperties(), "f_139798_")); // properties

            properties.setProperty("difficulty", "normal");
            properties.setProperty("enable-command-block", "true");
            properties.setProperty("gamemode", "creative");
            properties.setProperty("generate-structures", "false");
            properties.setProperty("level-name", TEST_WORLD);
            properties.setProperty("max-tick-time", "0");
            properties.setProperty("online-mode", "false");

            settingsHolder.update(old -> new DedicatedServerProperties(properties)); // Same effect as forceSave()

            File universeFile = new File(".");
            if (new File(universeFile, TEST_WORLD).getAbsolutePath().equals(new File(TEST_WORLD).getAbsolutePath()))
            {
                LOGGER.error("Invalid world directory specified, must not be null, empty or the same directory as your universe! " + TEST_WORLD);
                return;
            }
            LevelStorageSource levelStorage = LevelStorageSource.createDefault(universeFile.toPath());
            LevelStorageSource.LevelStorageAccess levelStorageAccess = levelStorage.createAccess(TEST_WORLD);
            LevelSummary summary = levelStorageAccess.getSummary();
            if (summary != null && summary.isIncompatibleWorldHeight())
            {
                LOGGER.info("Loading of worlds with extended height is disabled.");
                return;
            }

            DataPackConfig levelDataPacks = levelStorageAccess.getDataPacks();
            PackRepository packRepo = new PackRepository(PackType.SERVER_DATA, new ServerPacksSource(), new FolderRepositorySource(levelStorageAccess.getLevelPath(LevelResource.DATAPACK_DIR).toFile(), PackSource.WORLD));
            DataPackConfig dataPacks = MinecraftServer.configurePackRepository(packRepo, levelDataPacks == null ? DataPackConfig.DEFAULT : levelDataPacks, false);
            CompletableFuture<ServerResources> serverResourcesFuture = ServerResources.loadResources(packRepo.openAllSelected(), builtinRegistries, Commands.CommandSelection.DEDICATED, settingsHolder.getProperties().functionPermissionLevel, Util.backgroundExecutor(), Runnable::run);

            ServerResources serverResources;
            try
            {
                serverResources = serverResourcesFuture.get();
            }
            catch (Exception exception)
            {
                LOGGER.warn("Failed to load datapacks, can't proceed with server load. You can either fix your datapacks or reset to vanilla with --safeMode", exception);
                packRepo.close();
                return;
            }

            serverResources.updateGlobals();
            settingsHolder.getProperties().getWorldGenSettings(builtinRegistries);

            final UnitTestServer server = MinecraftServer.spin(thread -> new UnitTestServer(thread, levelStorageAccess, packRepo, serverResources, builtinRegistries));

            Thread shutdownThread = new Thread("Server Shutdown Thread")
            {
                public void run()
                {
                    server.halt(true);
                    LogManager.shutdown(); // we're manually managing the logging shutdown on the server. Make sure we do it here at the end.
                }
            };
            shutdownThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
            Runtime.getRuntime().addShutdownHook(shutdownThread);
        }
        catch (Exception e)
        {
            LOGGER.fatal("Failed to start the minecraft server", e);
        }
    }
}
