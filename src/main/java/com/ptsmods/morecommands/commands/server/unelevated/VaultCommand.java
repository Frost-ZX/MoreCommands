package com.ptsmods.morecommands.commands.server.unelevated;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ptsmods.morecommands.MoreCommands;
import com.ptsmods.morecommands.miscellaneous.Command;
import com.ptsmods.morecommands.miscellaneous.MoreGameRules;
import com.ptsmods.morecommands.util.DataTrackerHelper;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Objects;

public class VaultCommand extends Command {
	public static final List<ScreenHandlerType<GenericContainerScreenHandler>> types = ImmutableList.of(ScreenHandlerType.GENERIC_9X1, ScreenHandlerType.GENERIC_9X2,
			ScreenHandlerType.GENERIC_9X3, ScreenHandlerType.GENERIC_9X4, ScreenHandlerType.GENERIC_9X5, ScreenHandlerType.GENERIC_9X6);

	@Override
	public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literalReq("vault")
				.then(argument("vault", IntegerArgumentType.integer(1))
						.executes(ctx -> execute(ctx, ctx.getSource().getPlayer()))
						.then(argument("player", EntityArgumentType.player())
								.requires(IS_OP)
								.executes(ctx -> execute(ctx, EntityArgumentType.getPlayer(ctx, "player"))))));
	}

	public int execute(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity owner) throws CommandSyntaxException {
		int vault = ctx.getArgument("vault", Integer.class);
		int maxVaults = getCountFromPerms(ctx.getSource(), "morecommands.vault.max.", ctx.getSource().getWorld().getGameRules().getInt(MoreGameRules.vaultsRule));
		if (maxVaults == 0) sendError(ctx, "Vaults are disabled on this server.");
		else if (vault > maxVaults) sendError(ctx, (owner == ctx.getSource().getPlayer() ? "You" : MoreCommands.textToString(owner.getDisplayName(), Style.EMPTY.withColor(Formatting.RED), true)) +
				" may only have " + Formatting.DARK_RED + ctx.getSource().getWorld().getGameRules().getInt(MoreGameRules.vaultsRule) + Formatting.RED + " vaults.");
		else {
			int rows = getCountFromPerms(ctx.getSource(), "morecommands.vault.rows.", ctx.getSource().getWorld().getGameRules().getInt(MoreGameRules.vaultRowsRule));
			ctx.getSource().getPlayer().openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, player) -> new GenericContainerScreenHandler(types.get(rows - 1), syncId, inv,
					Objects.requireNonNull(getVault(vault, owner)), rows), literalText("" + DF + Formatting.BOLD + "Vault " + SF + Formatting.BOLD + vault).build()));
			return 1;
		}
		return 0;
	}

	public static Inventory getVault(int id, PlayerEntity player) {
		int maxVaults = player.getEntityWorld().getGameRules().getInt(MoreGameRules.vaultsRule);
		if (id > maxVaults) return null;
		int rows = player.getEntityWorld().getGameRules().getInt(MoreGameRules.vaultRowsRule);
		NbtList list = player.getDataTracker().get(DataTrackerHelper.VAULTS).getList("Vaults", 9);
		if (list.size() < maxVaults)
			for (int i = 0; i < maxVaults - list.size() + 1; i++)
				list.add(new NbtList());
		SimpleInventory inv = new SimpleInventory(rows * 9);
		NbtList vault = list.getList(id-1);
		for (int i = 0; i < vault.size(); i++)
			inv.setStack(i, ItemStack.fromNbt(vault.getCompound(i)));
		inv.addListener(inventory -> {
			list.remove(id-1);
			NbtList stacks = new NbtList();
			for (int i = 0; i < inv.size(); i++)
				stacks.add(inv.getStack(i).writeNbt(new NbtCompound()));
			list.add(id-1, stacks);
			player.getDataTracker().set(DataTrackerHelper.VAULTS, MoreCommands.wrapTag("Vaults", list));
		});
		return inv;
	}
}
