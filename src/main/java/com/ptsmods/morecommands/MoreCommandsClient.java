package com.ptsmods.morecommands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.ptsmods.morecommands.callbacks.ChatMessageSendCallback;
import com.ptsmods.morecommands.callbacks.ClientCommandRegistrationCallback;
import com.ptsmods.morecommands.commands.client.*;
import com.ptsmods.morecommands.gui.InfoHud;
import com.ptsmods.morecommands.miscellaneous.*;
import io.netty.buffer.Unpooled;
import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Environment(EnvType.CLIENT)
public class MoreCommandsClient implements ClientModInitializer {

    public static final Logger log = LogManager.getLogger();
    public static final ClientSidePacketRegistry CPR = ClientSidePacketRegistry.INSTANCE;
    public static final KeyBinding toggleInfoHudBinding = new KeyBinding("key.morecommands.toggleInfoHud", GLFW.GLFW_KEY_O, "MoreCommands");;
    private static double speed = 0d;
    private static EasterEggSound easterEggSound = null;
    public static final CommandDispatcher<ClientCommandSource> clientCommandDispatcher = new CommandDispatcher<>();
    private static final Map<String, Integer> keys = new LinkedHashMap<>();
    private static final Map<Integer, String> keysReverse = new LinkedHashMap<>();

    static {
        for (Field f : GLFW.class.getFields())
            if (f.getName().startsWith("GLFW_KEY_") || f.getName().startsWith("GLFW_MOUSE_BUTTON_")) {
                int keyCode;
                try {
                    keyCode = f.getInt(null) + (f.getName().contains("MOUSE") ? GLFW.GLFW_KEY_LAST+1 : 0);
                } catch (IllegalAccessException e) {
                    log.catching(e);
                    continue;
                }
                String name = f.getName().substring(f.getName().contains("MOUSE") ? 18 : 9);
                if (keyCode >= 0 && !name.equals("LAST")) {
                    if (keys.containsValue(keyCode)) keys.remove(keysReverse.remove(keyCode)); // Aliases :/
                    keysReverse.put(keyCode, name);
                    keys.put(keysReverse.get(keyCode), keyCode);
                }
            }
    }

    @Override
    public void onInitializeClient() {
        ClientOptions.read();
        DiscordRPC.discordInitialize("754048885755871272", new DiscordEventHandlers.Builder()
                .setReadyEventHandler(user -> log.info("Connected to Discord RPC as " + user.username + "#" + user.discriminator + " (" + user.userId + ")."))
                .setDisconnectedEventHandler((errorCode, message) -> log.info("Disconnected from Discord RPC with error code " + errorCode + ": " + message))
                .setErroredEventHandler((errorCode, message) -> log.info("An error occurred on the Discord RPC with error code " + errorCode + ": " + message)).build(), true);
        updatePresence();
        Runtime.getRuntime().addShutdownHook(new Thread(DiscordRPC::discordShutdown));
        Language.setInstance(Language.getInstance()); // Wrap the current instance so it can translate all enchant levels and spawner names. :3 (Look at MixinLanguage)
        KeyBindingHelper.registerKeyBinding(toggleInfoHudBinding);
        HudRenderCallback.EVENT.register((stack, tickDelta) -> {
            if (ClientOptions.Tweaks.enableInfoHud) InfoHud.instance.render(stack, tickDelta);
        });
        ClientTickEvents.START_WORLD_TICK.register(world -> {
            if (toggleInfoHudBinding.wasPressed()) {
                ClientOptions.Tweaks.enableInfoHud = !ClientOptions.Tweaks.enableInfoHud;
                ClientOptions.write();
            }
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p != null) {
                double x = p.getX() - p.prevX;
                double y = p.getY() - p.prevY;
                double z = p.getZ() - p.prevZ;
                speed = MathHelper.sqrt(x * x + y * y + z * z) * 20; // Apparently, Pythagoras' theorem does have some use. Who would've thunk?
            }
        });
        ClientCommandRegistrationCallback.EVENT.register(dispatcher -> {
            for (Class<? extends ClientCommand> cmd : MoreCommands.getCommandClasses("client", ClientCommand.class))
                try {
                     MoreCommands.getInstance(cmd).cRegister(dispatcher);
                } catch (InstantiationException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    log.catching(e);
                }
        });
        CPR.register(new Identifier("morecommands:formatting_update"), (ctx, buf) -> {
            int id = buf.readByte();
            Formatting colour = FormattingColour.values()[buf.readByte()].toFormatting();
            switch (id) {
                case 0:
                    MoreCommands.DF = Command.DF = colour;
                    MoreCommands.DS = Command.DS = MoreCommands.DS.withColor(MoreCommands.DF);
                    break;
                case 1:
                    MoreCommands.SF = Command.SF = colour;
                    MoreCommands.SS = Command.SS = MoreCommands.SS.withColor(MoreCommands.SF);
                    break;
            }
        });
        ChatMessageSendCallback.EVENT.register(message -> {
            if (message.startsWith("/easteregg")) {
                if (easterEggSound == null) MinecraftClient.getInstance().getSoundManager().play(easterEggSound = new EasterEggSound());
                else {
                    MinecraftClient.getInstance().getSoundManager().stop(easterEggSound);
                    easterEggSound = null;
                }
                return null;
            }
            return message;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (ClientOptions.Tweaks.sitOnStairs && Chair.isValid(world.getBlockState(hitResult.getBlockPos())) && CPR.canServerReceive(new Identifier("morecommands:sit_on_stairs"))) {
                CPR.sendToServer(CPR.toPacket(new Identifier("morecommands:sit_on_stairs"), new PacketByteBuf(Unpooled.buffer()).writeBlockPos(hitResult.getBlockPos())));
                return ActionResult.CONSUME;
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.START_WORLD_TICK.register(world -> {
            for (Entity entity : world.getEntities())
                if (entity instanceof PlayerEntity && MoreCommands.isCool(entity))
                    for (int i = 0; i < 2; i++)
                        MinecraftClient.getInstance().particleManager.addParticle(new VexParticle(entity));
        });
    }

    public static void updatePresence() {
        if (ClientOptions.RichPresence.enableRPC) {
            MinecraftClient client = MinecraftClient.getInstance();
            DiscordRichPresence.Builder builder;
            if (client.world == null) builder = new DiscordRichPresence.Builder("On the main menu").setBigImage("minecraft_logo", null);
            else if (client.getCurrentServerEntry() != null) {
                builder = new DiscordRichPresence.Builder("Multiplayer").setBigImage("in_game", null);
                if (ClientOptions.RichPresence.showDetails) builder.setDetails(client.getCurrentServerEntry().address);
            } else {
                builder = new DiscordRichPresence.Builder("Singleplayer").setBigImage("in_game", null);
                if (ClientOptions.RichPresence.showDetails) builder.setDetails(Objects.requireNonNull(client.getServer()).getSaveProperties().getLevelName());
            }
            if (ClientOptions.RichPresence.advertiseMC) builder.setSmallImage("morecommands_logo", "Download at https://bit.ly/MoreCommands");
            DiscordRPC.discordUpdatePresence(builder.setStartTimestamps(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() / 1000L).build());
        } else DiscordRPC.discordClearPresence();
    }

    public static double getSpeed() {
        return speed;
    }

    public static int getKeyCodeForKey(String key) {
        return keys.get(key);
    }

    public static String getKeyForKeyCode(int keyCode) {
        return keysReverse.get(keyCode);
    }

    public static List<String> getKeys() {
        return ImmutableList.copyOf(keys.keySet());
    }

}
