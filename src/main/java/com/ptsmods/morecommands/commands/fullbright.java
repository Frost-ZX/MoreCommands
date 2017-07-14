package com.ptsmods.morecommands.commands;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class fullbright {

	public static Object instance;

	public fullbright() {
	}

	public static class Commandfullbright implements ICommand{
		public boolean isUsernameIndex(int sender) {
			return false;
		}

		public java.util.List getAliases() {
			ArrayList aliases = new ArrayList();
			aliases.add("fb");
			aliases.add("nightvision");
			aliases.add("nv");
			return aliases;
		}

		public java.util.List getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
			return new ArrayList();
		}

		public boolean isUsernameIndex(String[] string, int index) {
			return true;
		}

		public String getName() {
			return "fullbright";
		}

		public String getUsage(ICommandSender sender) {
			return "/fullbright Makes your screen bright.";
		}

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
			EntityPlayer player = (EntityPlayer) sender;
			Minecraft.getMinecraft().gameSettings.gammaSetting = 1000;
			sender.sendMessage(new TextComponentString("Now you can see anything! To remove the effect set your gamma setting to something different."));
		}

		@Override
		public int compareTo(ICommand arg0) {
			return 0;
		}

		@Override
		public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
			return true;
		}

	}

}