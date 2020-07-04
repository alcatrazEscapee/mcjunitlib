package com.alcatrazescapee.mcjunitlib;

import java.awt.*;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import net.minecraft.server.ServerEula;
import net.minecraft.server.ServerPropertiesProvider;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.DefaultUncaughtExceptionHandler;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.chunk.listener.IChunkStatusListenerFactory;
import net.minecraft.world.chunk.listener.LoggingChunkStatusListener;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class DedicatedTestServer extends DedicatedServer
{
    private static final Level UNIT_TEST = Level.forName("UNITTEST", 50);
    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args)
    {
        LOGGER.log(UNIT_TEST, "Dedicated Test Server Starting");

        // Class load order
        try
        {
            Class.forName("net.minecraft.server.MinecraftServer");
        }
        catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }

        OptionParser optionparser = new OptionParser();
        OptionSpec<Void> optionspec = optionparser.accepts("nogui");
        OptionSpec<Void> optionspec1 = optionparser.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        OptionSpec<Void> optionspec2 = optionparser.accepts("demo");
        OptionSpec<Void> optionspec3 = optionparser.accepts("bonusChest");
        OptionSpec<Void> optionspec4 = optionparser.accepts("forceUpgrade");
        OptionSpec<Void> optionspec5 = optionparser.accepts("eraseCache");
        OptionSpec<Void> optionspec6 = optionparser.accepts("help").forHelp();
        OptionSpec<String> optionspec7 = optionparser.accepts("singleplayer").withRequiredArg();
        OptionSpec<String> optionspec8 = optionparser.accepts("universe").withRequiredArg().defaultsTo(".");
        OptionSpec<String> optionspec9 = optionparser.accepts("world").withRequiredArg();
        OptionSpec<Integer> optionspec10 = optionparser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1);
        OptionSpec<String> optionspec11 = optionparser.accepts("serverId").withRequiredArg();
        OptionSpec<String> optionspec12 = optionparser.nonOptions();
        optionparser.accepts("gameDir").withRequiredArg().ofType(File.class).defaultsTo(new File(".")); //Forge: Consume this argument, we use it in the launcher, and the client side.

        try {
            OptionSet optionset = optionparser.parse(args);
            if (optionset.has(optionspec6)) {
                optionparser.printHelpOn(System.err);
                return;
            }

            Path path = Paths.get("server.properties");
            ServerPropertiesProvider serverpropertiesprovider = new ServerPropertiesProvider(path);
            if (optionset.has(optionspec1) || !Files.exists(path)) serverpropertiesprovider.save();
            Path path1 = Paths.get("eula.txt");
            ServerEula servereula = new ServerEula(path1);
            if (optionset.has(optionspec1)) {
                LOGGER.info("Initialized '" + path.toAbsolutePath().toString() + "' and '" + path1.toAbsolutePath().toString() + "'");
                return;
            }

            if (!servereula.hasAcceptedEULA()) {
                LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                return;
            }

            CrashReport.func_230188_h_();
            Bootstrap.register();
            Bootstrap.checkTranslations();
            String s = optionset.valueOf(optionspec8);
            YggdrasilAuthenticationService yggdrasilauthenticationservice = new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString());
            MinecraftSessionService minecraftsessionservice = yggdrasilauthenticationservice.createMinecraftSessionService();
            GameProfileRepository gameprofilerepository = yggdrasilauthenticationservice.createProfileRepository();
            PlayerProfileCache playerprofilecache = new PlayerProfileCache(gameprofilerepository, new File(s, USER_CACHE_FILE.getName()));
            String s1 = Optional.ofNullable(optionset.valueOf(optionspec9)).orElse(serverpropertiesprovider.getProperties().worldName);
            if (s1 == null || s1.isEmpty() || new File(s, s1).getAbsolutePath().equals(new File(s).getAbsolutePath())) {
                LOGGER.error("Invalid world directory specified, must not be null, empty or the same directory as your universe! " + s1);
                return;
            }
            final DedicatedTestServer dedicatedserver = new DedicatedTestServer(new File(s), serverpropertiesprovider, DataFixesManager.getDataFixer(), yggdrasilauthenticationservice, minecraftsessionservice, gameprofilerepository, playerprofilecache, LoggingChunkStatusListener::new, s1);
            dedicatedserver.setServerOwner(optionset.valueOf(optionspec7));
            dedicatedserver.setServerPort(optionset.valueOf(optionspec10));
            dedicatedserver.setDemo(optionset.has(optionspec2));
            dedicatedserver.canCreateBonusChest(optionset.has(optionspec3));
            dedicatedserver.setForceWorldUpgrade(optionset.has(optionspec4));
            dedicatedserver.setEraseCache(optionset.has(optionspec5));
            dedicatedserver.setServerId(optionset.valueOf(optionspec11));
            boolean flag = !optionset.has(optionspec) && !optionset.valuesOf(optionspec12).contains("nogui");
            if (flag && !GraphicsEnvironment.isHeadless()) {
                dedicatedserver.setGuiEnabled();
            }

            dedicatedserver.startServerThread();
            Thread thread = new Thread("Server Shutdown Thread") {
                public void run() {
                    dedicatedserver.initiateShutdown(true);
                    LogManager.shutdown(); // we're manually managing the logging shutdown on the server. Make sure we do it here at the end.
                }
            };
            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
            Runtime.getRuntime().addShutdownHook(thread);
        } catch (Exception exception) {
            LOGGER.fatal("Failed to start the minecraft server", exception);
        }
    }

    private DedicatedTestServer(File p_i50720_1_, ServerPropertiesProvider p_i50720_2_, DataFixer dataFixerIn, YggdrasilAuthenticationService p_i50720_4_, MinecraftSessionService p_i50720_5_, GameProfileRepository p_i50720_6_, PlayerProfileCache p_i50720_7_, IChunkStatusListenerFactory p_i50720_8_, String p_i50720_9_)
    {
        super(p_i50720_1_, p_i50720_2_, dataFixerIn, p_i50720_4_, p_i50720_5_, p_i50720_6_, p_i50720_7_, p_i50720_8_, p_i50720_9_);
    }

    @Override
    public void run()
    {
        LOGGER.log(UNIT_TEST, "DedicatedTestServer -> ServerThread Starting");
        try {
            if (this.init()) {
                net.minecraftforge.fml.server.ServerLifecycleHooks.handleServerStarted(this);
                new JUnitTestRunner().runAllTests();
                net.minecraftforge.fml.server.ServerLifecycleHooks.handleServerStopping(this);
                net.minecraftforge.fml.server.ServerLifecycleHooks.expectServerStopped(); // has to come before finalTick to avoid race conditions
            } else {
                net.minecraftforge.fml.server.ServerLifecycleHooks.expectServerStopped(); // has to come before finalTick to avoid race conditions
                this.finalTick(null);
            }
        } catch (net.minecraftforge.fml.StartupQuery.AbortedException e) {
            // ignore silently
            net.minecraftforge.fml.server.ServerLifecycleHooks.expectServerStopped(); // has to come before finalTick to avoid race conditions
        } catch (Throwable throwable1) {
            LOGGER.error("Encountered an unexpected exception", throwable1);
            CrashReport crashreport;
            if (throwable1 instanceof ReportedException) {
                crashreport = this.addServerInfoToCrashReport(((ReportedException)throwable1).getCrashReport());
            } else {
                crashreport = this.addServerInfoToCrashReport(new CrashReport("Exception in server tick loop", throwable1));
            }

            File file1 = new File(new File(this.getDataDirectory(), "crash-reports"), "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-server.txt");
            if (crashreport.saveToFile(file1)) {
                LOGGER.error("This crash report has been saved to: {}", file1.getAbsolutePath());
            } else {
                LOGGER.error("We were unable to save this crash report to disk.");
            }

            net.minecraftforge.fml.server.ServerLifecycleHooks.expectServerStopped(); // has to come before finalTick to avoid race conditions
            this.finalTick(crashreport);
        } finally {
            try {
                this.stopServer();
            } catch (Throwable throwable) {
                LOGGER.error("Exception stopping the server", throwable);
            } finally {
                net.minecraftforge.fml.server.ServerLifecycleHooks.handleServerStopped(this);
                this.systemExitNow();
            }
        }
    }

    @Override
    protected void finalTick(@Nullable CrashReport report)
    {
        // Noop
    }
}
