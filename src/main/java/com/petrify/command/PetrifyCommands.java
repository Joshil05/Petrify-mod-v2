package com.petrify.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.petrify.PetrifyMod;
import com.petrify.util.PetrifyHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PetrifyMod.MOD_ID)
public class PetrifyCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("petrify")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(ctx -> petrify(ctx, 0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                .executes(ctx -> petrify(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))));

        dispatcher.register(Commands.literal("unpetrify")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(PetrifyCommands::unpetrify)));
    }

    private static int petrify(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (entity instanceof LivingEntity living) {
                PetrifyHelper.petrify(living, seconds);
                count++;
            }
        }
        return report(ctx, count, "commands.petrify.petrify.success");
    }

    private static int unpetrify(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (entity instanceof net.minecraft.server.level.ServerPlayer collected
                    && com.petrify.util.StatueManager.isCollected(collected)) {
                com.petrify.util.StatueManager.free(collected);
                count++;
            } else if (entity instanceof LivingEntity living && PetrifyHelper.isPetrified(living)) {
                PetrifyHelper.restore(living);
                count++;
            }
        }
        return report(ctx, count, "commands.petrify.unpetrify.success");
    }

    private static int report(CommandContext<CommandSourceStack> ctx, int count, String successKey) {
        if (count == 0) {
            ctx.getSource().sendFailure(Component.translatable("commands.petrify.no_targets"));
            return 0;
        }
        final int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.translatable(successKey, finalCount), true);
        return count;
    }
}
