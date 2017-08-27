package com.ptsmods.morecommands.commands;

import java.util.ArrayList;

import com.ptsmods.morecommands.miscellaneous.CommandType;
import com.ptsmods.morecommands.miscellaneous.Permission;
import com.ptsmods.morecommands.miscellaneous.Reference;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public class tpaccept {

	public tpaccept() {
	}

	public static class Commandtpaccept extends com.ptsmods.morecommands.miscellaneous.CommandBase {

		@Override
		public int getRequiredPermissionLevel() {
			return 0;
		}

		@Override
		public java.util.List getAliases() {
			ArrayList aliases = new ArrayList();
			aliases.add("tpyes");
			return aliases;
		}

		@Override
		public java.util.List getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
			return new ArrayList();
		}

		@Override
		public String getName() {
			return "tpaccept";
		}

		@Override
		public String getUsage(ICommandSender sender) {
			return usage;
		}

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			if (Reference.tpRequests.get(sender.getName()) == null)
				Reference.sendMessage(sender, "You do not have a tpa request open.");
			else
				try {
					EntityPlayer requester = CommandBase.getPlayer(server, sender, Reference.tpRequests.get(sender.getName()));
					Reference.sendMessage(requester, sender.getName() + " has accepted your tpa request.");
					EntityPlayer player = (EntityPlayer) sender;
					requester.setPositionAndUpdate(player.getPosition().getX() + 0.5, player.getPosition().getY(), player.getPosition().getZ() + 0.5);
					Reference.tpRequests.remove(player.getName());
					Reference.sendMessage(player, requester.getName() + " has been teleported to you.");
				} catch (PlayerNotFoundException e) {
					Reference.sendMessage(sender, "Error getting the person who tried to teleport to you, are they offline?");
				}

		}

		@Override
		public CommandType getCommandType() {
			return CommandType.SERVER;
		}

		@Override
		public Permission getPermission() {
			return new Permission(Reference.MOD_ID, "tpaccept", "Permission to use the tpaccept command.", true);
		}

		protected String usage = "/tpaccept Accept a tpa request.";

	}

}