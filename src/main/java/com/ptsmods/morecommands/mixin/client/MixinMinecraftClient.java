package com.ptsmods.morecommands.mixin.client;

import com.ptsmods.morecommands.MoreCommands;
import com.ptsmods.morecommands.MoreCommandsClient;
import com.ptsmods.morecommands.callbacks.PostInitCallback;
import com.ptsmods.morecommands.callbacks.RenderTickCallback;
import com.ptsmods.morecommands.commands.client.SearchCommand;
import com.ptsmods.morecommands.clientoption.ClientOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftClient.class, priority = 1100)
public class MixinMinecraftClient {
	@Shadow private IntegratedServer server;

	@Inject(at = @At("TAIL"), method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V")
	public void disconnect(Screen screen, CallbackInfo cbi) {
		// Reset to defaults when leaving the world.
		MoreCommands.setFormattings(Formatting.GOLD, Formatting.YELLOW);
		SearchCommand.lines.clear();
		MoreCommandsClient.updatePresence();
		ClientOptions.getOptions().forEach(option -> option.setDisabled(false));
		MoreCommandsClient.clearDisabledCommands();
	}

	@Inject(at = @At("HEAD"), method = "render(Z)V")
	public void renderPre(boolean tick, CallbackInfo cbi) {
		RenderTickCallback.PRE.invoker().render(tick);
	}

	@Inject(at = @At("TAIL"), method = "render(Z)V")
	public void renderPost(boolean tick, CallbackInfo cbi) {
		RenderTickCallback.POST.invoker().render(tick);
	}

	@Inject(at = @At("TAIL"), method = "setCurrentServerEntry(Lnet/minecraft/client/network/ServerInfo;)V")
	public void setCurrentServerEntry(ServerInfo info, CallbackInfo cbi) {
		if (info != null) MoreCommandsClient.updatePresence();
	}

	@Inject(at = @At("TAIL"), method = "joinWorld(Lnet/minecraft/client/world/ClientWorld;)V")
	public void joinWorld(ClientWorld world, CallbackInfo cbi) {
		MoreCommandsClient.updatePresence();
	}

	@Inject(at = @At(value = "INVOKE", target = "Lnet/fabricmc/loader/entrypoint/minecraft/hooks/EntrypointClient;start(Ljava/io/File;Ljava/lang/Object;)V", remap = false, shift = At.Shift.AFTER), method = "<init>")
	private void postInit(RunArgs args, CallbackInfo ci) {
		PostInitCallback.EVENT.invoker().postInit();
	}

//	@Inject(at = @At("STORE"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/Packet;)V")), method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/DynamicRegistryManager$Impl;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V")
	@ModifyVariable(at = @At("STORE"), method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/DynamicRegistryManager$Impl;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V")
	private ClientConnection startIntegratedServer_integratedServerConnection(ClientConnection connection) {
		MoreCommandsClient.scheduleWorldInitCommands = true;
		return connection;
	}
}
