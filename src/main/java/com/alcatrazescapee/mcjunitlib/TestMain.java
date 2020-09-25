package com.alcatrazescapee.mcjunitlib;

import java.awt.*;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.command.Commands;
import net.minecraft.crash.CrashReport;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerPropertiesProvider;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.ServerProperties;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.DefaultUncaughtExceptionHandler;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.codec.DatapackCodec;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.WorldSettingsImport;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.listener.LoggingChunkStatusListener;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import net.minecraft.world.storage.FolderName;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraft.world.storage.SaveFormat;
import net.minecraft.world.storage.ServerWorldInfo;

import net.minecraftforge.fml.server.ServerModLoader;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.serialization.Lifecycle;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class TestMain
{
    private static final Level UNIT_TEST = Level.forName("UNITTEST", 50);
    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args)
    {
        LOGGER.log(UNIT_TEST, "TestMain launching");

        OptionParser optionparser = new OptionParser();
        OptionSpec<String> universeOption = optionparser.accepts("universe").withRequiredArg().defaultsTo(".");
        OptionSpec<String> worldOption = optionparser.accepts("world").withRequiredArg();
        optionparser.accepts("allowUpdates").withRequiredArg().ofType(Boolean.class).defaultsTo(Boolean.TRUE); // Forge: allow mod updates to proceed
        optionparser.accepts("gameDir").withRequiredArg().ofType(File.class).defaultsTo(new File(".")); //Forge: Consume this argument, we use it in the launcher, and the client side.

        try
        {
            OptionSet optionset = optionparser.parse(args);

            CrashReport.preload();
            Bootstrap.bootStrap();
            Bootstrap.validate();
            Util.startTimerHackThread();
            ServerModLoader.load(); // Load mods before we load almost anything else anymore. Single spot now. Only loads if they haven't passed the initserver param
            DynamicRegistries.Impl dynamicregistries$impl = DynamicRegistries.builtin();
            Path path = Paths.get("server.properties");
            ServerPropertiesProvider serverpropertiesprovider = new ServerPropertiesProvider(dynamicregistries$impl, path);
            serverpropertiesprovider.forceSave();

            File file1 = new File(optionset.valueOf(universeOption));
            YggdrasilAuthenticationService yggdrasilauthenticationservice = new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString());
            MinecraftSessionService minecraftsessionservice = yggdrasilauthenticationservice.createMinecraftSessionService();
            GameProfileRepository gameprofilerepository = yggdrasilauthenticationservice.createProfileRepository();
            PlayerProfileCache playerprofilecache = new PlayerProfileCache(gameprofilerepository, new File(file1, MinecraftServer.USERID_CACHE_FILE.getName()));
            String s = Optional.ofNullable(optionset.valueOf(worldOption)).orElse(serverpropertiesprovider.getProperties().levelName);
            if (s == null || s.isEmpty() || new File(file1, s).getAbsolutePath().equals(new File(s).getAbsolutePath()))
            {
                LOGGER.log(UNIT_TEST, "Error: Invalid world directory specified, must not be null, empty or the same directory as your universe! " + s);
                return;
            }
            SaveFormat saveformat = SaveFormat.createDefault(file1.toPath());
            SaveFormat.LevelSave saveformat$levelsave = saveformat.createAccess(s);
            MinecraftServer.convertFromRegionFormatIfNeeded(saveformat$levelsave);
            DatapackCodec datapackcodec = saveformat$levelsave.getDataPacks();

            ResourcePackList resourcepacklist = new ResourcePackList(new ServerPackFinder(), new FolderPackFinder(saveformat$levelsave.getLevelPath(FolderName.DATAPACK_DIR).toFile(), IPackNameDecorator.WORLD));
            DatapackCodec datapackcodec1 = MinecraftServer.configurePackRepository(resourcepacklist, datapackcodec == null ? DatapackCodec.DEFAULT : datapackcodec, false);
            CompletableFuture<DataPackRegistries> completablefuture = DataPackRegistries.loadResources(resourcepacklist.openAllSelected(), Commands.EnvironmentType.DEDICATED, serverpropertiesprovider.getProperties().functionPermissionLevel, Util.backgroundExecutor(), Runnable::run);

            DataPackRegistries datapackregistries;
            try
            {
                datapackregistries = completablefuture.get();
            }
            catch (Exception exception)
            {
                LOGGER.log(UNIT_TEST, "Failed to load datapacks, can't proceed with server load. You can either fix your datapacks or reset to vanilla with --safeMode", exception);
                resourcepacklist.close();
                return;
            }

            datapackregistries.updateGlobals();
            WorldSettingsImport<INBT> worldsettingsimport = WorldSettingsImport.create(NBTDynamicOps.INSTANCE, datapackregistries.getResourceManager(), dynamicregistries$impl);
            IServerConfiguration iserverconfiguration = saveformat$levelsave.getDataTag(worldsettingsimport, datapackcodec1);
            if (iserverconfiguration == null)
            {
                WorldSettings worldsettings;
                DimensionGeneratorSettings dimensiongeneratorsettings;
                ServerProperties serverproperties = serverpropertiesprovider.getProperties();
                worldsettings = new WorldSettings(serverproperties.levelName, serverproperties.gamemode, serverproperties.hardcore, serverproperties.difficulty, false, new GameRules(), datapackcodec1);
                dimensiongeneratorsettings = serverproperties.worldGenSettings;
                iserverconfiguration = new ServerWorldInfo(worldsettings, dimensiongeneratorsettings, Lifecycle.stable());
            }

            saveformat$levelsave.saveDataTag(dynamicregistries$impl, iserverconfiguration);
            IServerConfiguration iserverconfiguration1 = iserverconfiguration;
            final DedicatedServer dedicatedserver = MinecraftServer.spin((thread_) -> new DedicatedTestServer(thread_, dynamicregistries$impl, saveformat$levelsave, resourcepacklist, datapackregistries, iserverconfiguration1, serverpropertiesprovider, DataFixesManager.getDataFixer(), minecraftsessionservice, gameprofilerepository, playerprofilecache, LoggingChunkStatusListener::new));
            Thread thread = new Thread("Server Shutdown Thread")
            {
                public void run()
                {
                    dedicatedserver.halt(true);
                    LogManager.shutdown(); // we're manually managing the logging shutdown on the server. Make sure we do it here at the end.
                }
            };
            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
            Runtime.getRuntime().addShutdownHook(thread);
        }
        catch (Exception exception1)
        {
            LOGGER.log(UNIT_TEST, "FATAL: Failed to start the minecraft server", exception1);
        }
    }
}
