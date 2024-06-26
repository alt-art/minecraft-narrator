package com.mitsuaky.stanleyparable.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class NarratorCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("minecraftnarrator")
                        .then(CustomTTSCommand.register())
                        .then(CustomPromptCommand.register())
                        .then(SystemPromptCommand.register())
                        .then(SetDebugModeCommand.register())
                        .then(SetAdventureModeCommand.register())
                        .then(InteractionCheckCommand.register())
                        .then(ForceInteractionCommand.register())
        );
    }
}