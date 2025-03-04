package com.ptsmods.morecommands.commands.server.unelevated;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ptsmods.morecommands.MoreCommands;
import com.ptsmods.morecommands.miscellaneous.Command;
import com.ptsmods.morecommands.util.CompatHolder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class EnderChestCommand extends Command {
	@Override
	public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.getRoot().addChild(MoreCommands.createAlias("ec", dispatcher.register(literalReq("enderchest").executes(ctx -> execute(ctx, null)).then(argument("player", EntityArgumentType.player()).requires(hasPermissionOrOp("morecommands.enderchest.others")).executes(ctx -> execute(ctx, EntityArgumentType.getPlayer(ctx, "player")))))));
	}

	private int execute(CommandContext<ServerCommandSource> ctx, PlayerEntity p) throws CommandSyntaxException {
		PlayerEntity player = p == null ? ctx.getSource().getPlayer() : p;
		ctx.getSource().getPlayer().openHandledScreen(new NamedScreenHandlerFactory() {
			@Override
			public Text getDisplayName() {
				return literalText("")
						.append(CompatHolder.getCompat().builderFromText(player.getDisplayName()))
						.append(literalText("'" + (MoreCommands.textToString(player.getDisplayName(), null, true).endsWith("s") ? "" : "s") + " enderchest"))
						.build();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
				return GenericContainerScreenHandler.createGeneric9x3(syncId, inv, player.getEnderChestInventory());
			}
		});
		return 1;
	}
}
