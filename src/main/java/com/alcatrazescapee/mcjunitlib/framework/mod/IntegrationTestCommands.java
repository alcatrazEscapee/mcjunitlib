package com.alcatrazescapee.mcjunitlib.framework.mod;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;

import com.alcatrazescapee.mcjunitlib.framework.IntegrationTestManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

/**
 * Part of the integration test mod features.
 * Needs to be initialized by calling {@link IntegrationTestManager#setup(String)}
 */
final class IntegrationTestCommands
{
    static void registerCommands(CommandDispatcher<CommandSource> dispatcher)
    {
        dispatcher.register(Commands.literal("integrationTest")
            .then(Commands.literal("setup")
                .executes(context -> setupAllTests(context.getSource()))
            )
            .then(Commands.literal("run")
                .executes(context -> runAllTests(context.getSource()))
            )
        );
    }

    private static int runAllTests(CommandSource source)
    {
        if (IntegrationTestManager.INSTANCE.runAllTests())
        {
            source.sendSuccess(new StringTextComponent("Running!"), true);
        }
        else
        {
            source.sendFailure(new StringTextComponent("Cannot run tests now! Tests are not properly setup."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setupAllTests(CommandSource source)
    {
        if (IntegrationTestManager.INSTANCE.verifyAllTests(source.getLevel(), source))
        {
            IntegrationTestManager.INSTANCE.setupAllTests(source.getLevel(), source);
            source.sendSuccess(new StringTextComponent("Setup Finished!"), true);
        }
        else
        {
            source.sendFailure(new StringTextComponent("One or more tests failed verification!"));
        }
        return Command.SINGLE_SUCCESS;
    }
}
