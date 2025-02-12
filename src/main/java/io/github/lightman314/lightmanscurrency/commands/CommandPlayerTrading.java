package io.github.lightman314.lightmanscurrency.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.lightman314.lightmanscurrency.commands.arguments.TradeIDArgument;
import io.github.lightman314.lightmanscurrency.common.playertrading.PlayerTrade;
import io.github.lightman314.lightmanscurrency.common.playertrading.PlayerTradeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandPlayerTrading {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> requestTradeCommand
                = Commands.literal("lctrade")
                .requires(CommandSourceStack::isPlayer)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(CommandPlayerTrading::requestPlayerTrade));

        LiteralArgumentBuilder<CommandSourceStack> acceptTradeCommand
                = Commands.literal("lctradeaccept")
                .requires(CommandSourceStack::isPlayer)
                .then(Commands.argument("tradeID", TradeIDArgument.argument())
                        .executes(CommandPlayerTrading::acceptPlayerTrade));

        dispatcher.register(requestTradeCommand);
        dispatcher.register(acceptTradeCommand);

    }

    private static int requestPlayerTrade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer host = context.getSource().getPlayerOrException();
        ServerPlayer guest = EntityArgument.getPlayer(context, "player");

        if(guest == host)
        {
            context.getSource().sendFailure(Component.translatable("command.lightmanscurrency.lctrade.self"));
            return 0;
        }

        int tradeID = PlayerTradeManager.CreateNewTrade(host, guest);

        host.sendSystemMessage(Component.translatable("command.lightmanscurrency.lctrade.host.notify", guest.getName()));
        guest.sendSystemMessage(Component.translatable("command.lightmanscurrency.lctrade.guest.notify", host.getName(), Component.translatable("command.lightmanscurrency.lctrade.guest.notify.here").withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.GREEN).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lctradeaccept " + tradeID)))).withStyle(ChatFormatting.GOLD));

        return 1;
    }

    private static int acceptPlayerTrade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer guest = context.getSource().getPlayerOrException();
        int tradeID = TradeIDArgument.getTradeID(context,"tradeID");

        PlayerTrade trade = PlayerTradeManager.GetTrade(tradeID);
        if(trade != null && trade.isGuest(guest))
        {
            if(trade.requestAccepted(guest))
                return 1;
            else
            {
                context.getSource().sendFailure(Component.translatable("command.lightmanscurrency.lctradeaccept.error"));
                return 0;
            }
        }
        else
        {
            context.getSource().sendFailure(Component.translatable("command.lightmanscurrency.lctradeaccept.notfound"));
            return 0;
        }
    }

}
