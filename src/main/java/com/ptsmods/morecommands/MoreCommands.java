package com.ptsmods.morecommands;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.ptsmods.morecommands.api.Holder;
import com.ptsmods.morecommands.api.IMoreCommands;
import com.ptsmods.morecommands.api.ReflectionHelper;
import com.ptsmods.morecommands.api.compat.Compat;
import com.ptsmods.morecommands.api.text.LiteralTextBuilder;
import com.ptsmods.morecommands.api.text.TextBuilder;
import com.ptsmods.morecommands.api.text.TranslatableTextBuilder;
import com.ptsmods.morecommands.arguments.*;
import com.ptsmods.morecommands.callbacks.PostInitCallback;
import com.ptsmods.morecommands.clientoption.ClientOptions;
import com.ptsmods.morecommands.commands.server.elevated.ReachCommand;
import com.ptsmods.morecommands.commands.server.elevated.SpeedCommand;
import com.ptsmods.morecommands.miscellaneous.Chair;
import com.ptsmods.morecommands.miscellaneous.Command;
import com.ptsmods.morecommands.miscellaneous.FormattingColour;
import com.ptsmods.morecommands.miscellaneous.MoreGameRules;
import com.ptsmods.morecommands.mixin.common.accessor.*;
import com.ptsmods.morecommands.mixin.compat.MixinEntityAccessor;
import com.ptsmods.morecommands.util.CompatHolder;
import com.ptsmods.morecommands.util.DataTrackerHelper;
import io.netty.buffer.Unpooled;
import net.arikia.dev.drpc.DiscordUser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.ModContainer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.TestCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MoreCommands implements IMoreCommands, ModInitializer {
	public static Formatting DF = ClientOptions.Tweaks.defColour.getValue().asFormatting();
	public static Formatting SF = ClientOptions.Tweaks.secColour.getValue().asFormatting();
	public static Style DS = Style.EMPTY.withColor(DF);
	public static Style SS = Style.EMPTY.withColor(SF);
	public static final boolean SERVER_ONLY;
	public static final Set<Block> blockBlacklist = new HashSet<>();
	public static final Set<Block> blockWhitelist = new HashSet<>();
	public static final Block lockedChest = new Block(AbstractBlock.Settings.of(Material.WOOD));
	public static final Item lockedChestItem = new BlockItem(lockedChest, new Item.Settings());
	public static final Item netherPortalItem = new BlockItem(Blocks.NETHER_PORTAL, new Item.Settings().fireproof()); // After all, why not? Why shouldn't a nether portal be fireproof?
	public static final ItemGroup unobtainableItems = FabricItemGroupBuilder.create(new Identifier("morecommands:unobtainable_items")).icon(() -> new ItemStack(lockedChestItem)).build();
	public static MinecraftServer serverInstance = null;
	public static final ScoreboardCriterion LATENCY;
	public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	public static final Map<PlayerEntity, DiscordUser> discordTags = new WeakHashMap<>();
	public static final Set<PlayerEntity> discordTagNoPerm = Collections.newSetFromMap(new WeakHashMap<>());
	public static final Map<String, DamageSource> DAMAGE_SOURCES = new HashMap<>();
	public static boolean creatingWorld = false;
	private static final Executor executor = Executors.newCachedThreadPool();
	private static final DecimalFormat sizeFormat = new DecimalFormat("#.###");
	private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static {
		File serverOnlyFile = new File("config/MoreCommands/SERVERONLY.txt");
		boolean serverOnly = false;
		if (serverOnlyFile.exists()) {
			Properties props = new Properties();
			try {
				props.load(new FileReader(serverOnlyFile));
				serverOnly = Boolean.parseBoolean(props.getProperty("serverOnly", "false"));
			} catch (IOException e) {
				LOG.error("Could not read server-only state.", e);
			}
		}
		SERVER_ONLY = serverOnly;

		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER && SERVER_ONLY) {
			LOG.info("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
			LOG.info("-=-RUNNING IN SERVER-ONLY MODE-=-");
			LOG.info("-=CLIENTS WILL NOT NEED THE MOD=-");
			LOG.info("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
		}

		MixinScoreboardCriterionAccessor.getCriteria().put("latency", LATENCY = MixinScoreboardCriterionAccessor.newInstance("latency", true, ScoreboardCriterion.RenderType.INTEGER));
	}

	public MoreCommands() {
		Holder.setMoreCommands(this);
	}

	@Override
	public void onInitialize() {
		MixinFormattingAccessor.setFormattingCodePattern(Pattern.compile("(?i)\u00A7[0-9A-FK-ORU]")); // Adding the 'U' for the rainbow formatting.
		// Instantiating class and thereby Compat
		//noinspection ResultOfMethodCallIgnored
		CompatHolder.getCompat();
		MoreGameRules.init();
		DataTrackerHelper.init();

		File dir = new File("config/MoreCommands");
		if (!dir.exists() && !dir.mkdirs()) throw new RuntimeException("Could not make config dir.");

		List<Command> serverCommands = getCommandClasses("server", Command.class).stream()
				.map(MoreCommands::getInstance)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			serverCommands.stream()
					.filter(cmd -> !cmd.isDedicatedOnly() || dedicated)
					.forEach(cmd -> {
						try {
							cmd.register(dispatcher, dedicated);
						} catch (Exception e) {
							LOG.error("Could not register command " + cmd.getClass().getName() + ".", e);
						}
					});

			if (FabricLoader.getInstance().isDevelopmentEnvironment()) TestCommand.register(dispatcher); // Cuz why not lol
		});

		PostInitCallback.EVENT.register(() -> {
			blockBlacklist.clear();
			blockWhitelist.clear();
			Registry.BLOCK.forEach(block -> {
				if (block instanceof FluidBlock || !((MixinAbstractBlockAccessor) block).isCollidable()) blockBlacklist.add(block);
				try {
					VoxelShape shape = block.getDefaultState().getCollisionShape(null, null);
					if (shape.getMax(Direction.Axis.Y) > 16) blockBlacklist.add(block); // Will fall through if teleported on. (E.g. fences)
					if (shape.getMin(Direction.Axis.Z) > 6 / 16d || shape.getMax(Direction.Axis.Z) < 10 / 16d || // Can't stand on this. (E.g. doors)
							shape.getMin(Direction.Axis.X) > 6 / 16d || shape.getMax(Direction.Axis.X) < 10 / 16d) blockBlacklist.add(block);
					if (shape.getMin(Direction.Axis.Y) == shape.getMax(Direction.Axis.Y)) blockBlacklist.add(block); // Will fall straight through.

					if (!block.getDefaultState().getMaterial().blocksMovement() || !Block.isShapeFullCube(shape)) blockWhitelist.add(block); // Block does not cause suffocation.
				} catch (Exception ignored) {} // Getting collision shape probably requires a world and position which we don't have.

				try {
					Method onEntityCollision = ReflectionHelper.getYarnMethod(block.getClass(), "onEntityCollision", "method_9548", BlockState.class, World.class, BlockPos.class, Entity.class);
					if (onEntityCollision != null && onEntityCollision.getDeclaringClass() != AbstractBlock.class) blockBlacklist.add(block);

					onEntityCollision = ReflectionHelper.getYarnMethod(block.getDefaultState().getClass(), "onEntityCollision", "method_26178", World.class, BlockPos.class, Entity.class);
					if (onEntityCollision != null && onEntityCollision.getDeclaringClass() != AbstractBlock.AbstractBlockState.class) blockBlacklist.add(block);
					// onEntityCollision method was overridden, block does something to entities on collision, assume it's malicious.
				} catch (Throwable ignored) {} // For some reason, this can throw NoClassDefFoundErrors.
			});
		});

		if (!isServerOnly()) {
			Registry.register(Registry.SOUND_EVENT, new Identifier("morecommands:copy"), new SoundEvent(new Identifier("morecommands:copy")));
			Registry.register(Registry.SOUND_EVENT, new Identifier("morecommands:ee"), new SoundEvent(new Identifier("morecommands:ee")));
			Registry.register(Registry.BLOCK, new Identifier("morecommands:locked_chest"), lockedChest);
			Registry.register(Registry.ITEM, new Identifier("morecommands:locked_chest"), lockedChestItem);
			Registry.register(Registry.ITEM, new Identifier("minecraft:nether_portal"), netherPortalItem);
			Registry.register(Registry.ATTRIBUTE, new Identifier("morecommands:reach"), ReachCommand.REACH_ATTRIBUTE);
			Registry.register(Registry.ATTRIBUTE, new Identifier("morecommands:swim_speed"), SpeedCommand.SpeedType.swimSpeedAttribute);

			S2CPlayChannelEvents.REGISTER.register((handler, sender, server, types) -> {
				if (types.contains(new Identifier("morecommands:formatting_update"))) sendFormattingUpdates(handler.player);
			});

			ServerPlayNetworking.registerGlobalReceiver(new Identifier("morecommands:sit_on_stairs"), (server, player, handler, buf, responseSender) -> {
				BlockPos pos = buf.readBlockPos();
				if (player.getPos().squaredDistanceTo(new Vec3d(pos.getX(), pos.getY(), pos.getZ())) > ReachCommand.getReach(player, true))
					return; // Out of reach

				BlockState state = player.getWorld().getBlockState(pos);
				if (MoreGameRules.checkBooleanWithPerm(player.getWorld().getGameRules(), MoreGameRules.doChairsRule, player) && Chair.isValid(state))
					Chair.createAndPlace(pos, player, player.getWorld());
			});

			ServerPlayNetworking.registerGlobalReceiver(new Identifier("morecommands:discord_data"), (server, player, handler, buf, responseSender) -> {
				DiscordUser user = new DiscordUser();
				if (buf.readBoolean()) discordTagNoPerm.add(player);
				else discordTagNoPerm.remove(player);
				user.userId = buf.readString();
				user.username = buf.readString();
				user.discriminator = buf.readString();
				user.avatar = buf.readString();
				discordTags.put(player, user);
			});

			PostInitCallback.EVENT.register(() -> {
				DefaultedList<ItemStack> defaultedList = DefaultedList.of();
				for (Item item : Registry.ITEM) {
					item.appendStacks(ItemGroup.SEARCH, defaultedList);
				}

				Registry.BLOCK.forEach(block -> {
					Identifier id = Registry.BLOCK.getId(block);
					if (!CompatHolder.getCompat().registryContainsId(Registry.ITEM, id)) Registry.register(Registry.ITEM, new Identifier(id.getNamespace(), "mcsynthetic_" + id.getPath()), new BlockItem(block, new Item.Settings()));
				});

				Registry.ITEM.forEach(item -> {
					if (item.getGroup() == null && item != Items.AIR) ((MixinItemAccessor) item).setGroup(unobtainableItems);
				});

				defaultedList = DefaultedList.of();
				for (Item item : Registry.ITEM) {
					item.appendStacks(ItemGroup.SEARCH, defaultedList);
				}
			});

			Compat compat = CompatHolder.getCompat();

			compat.registerArgumentType("morecommands:enum_argument", EnumArgumentType.class, EnumArgumentType.SERIALISER);
			compat.registerArgumentType("morecommands:cramped_string", CrampedStringArgumentType.class, CrampedStringArgumentType.SERIALISER);
			compat.registerArgumentType("morecommands:time_argument", TimeArgumentType.class, TimeArgumentType.SERIALISER);
			compat.registerArgumentType("morecommands:hexinteger", HexIntegerArgumentType.class, HexIntegerArgumentType.SERIALISER);
			compat.registerArgumentType("morecommands:ignorant_string", IgnorantStringArgumentType.class, IgnorantStringArgumentType.SERIALISER);
			compat.registerArgumentType("morecommands:painting_variant", PaintingVariantArgumentType.class, PaintingVariantArgumentType.SERIALISER);
			compat.registerArgumentType("morecommands:potion", PotionArgumentType.class, PotionArgumentType.SERIALISER);
		} else {
			UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
				BlockState state = world.getBlockState(hit.getBlockPos());
				if (!player.isSneaking() && player.getStackInHand(hand).isEmpty() && MoreGameRules.checkBooleanWithPerm(world.getGameRules(), MoreGameRules.doChairsRule, player) &&
						CompatHolder.getCompat().tagContains(new Identifier("minecraft:stairs"), state.getBlock()) && Chair.isValid(state)) {
					Chair.createAndPlace(hit.getBlockPos(), player, world);
					return ActionResult.SUCCESS;
				}
				return ActionResult.PASS;
			});
		}

		Set<ServerPlayerEntity> howlingPlayers = new HashSet<>();
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			// This does absolutely nothing whatsoever, just pass along. :)
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
				if (p.isSneaking()) {
					float pitch = MathHelper.wrapDegrees(((MixinEntityAccessor) p).getPitch_());
					float yaw = MathHelper.wrapDegrees(((MixinEntityAccessor) p).getYaw_());
					double moonWidth = Math.PI / 32 * -pitch;
					long dayTime = p.getWorld().getTime() % 24000; // getTimeOfDay() does not return a value between 0 and 24000 when using the /time add command.
					if (!howlingPlayers.contains(p) && getMoonPhase(p.getWorld().getLunarTime()) == 0 && dayTime > 12000 && pitch < 0 && Math.abs(yaw) >= (90 - moonWidth) && Math.abs(yaw) <= (90 + moonWidth)) {
						double moonPitch = -90 + Math.abs(dayTime - 18000) * 0.0175;
						if (pitch >= moonPitch-3 && pitch <= moonPitch+3) {
							p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 1f, 1f);
							howlingPlayers.add(p);
						}
					}
				} else howlingPlayers.remove(p);
		});

//		if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
//			// Example: %morecommands:reach/1%
//			PlaceholderAPI.register(new Identifier("morecommands:reach"), ctx -> ctx.getPlayer() == null ? PlaceholderResult.invalid("A player must be passed.") :
//					PlaceholderResult.value(new DecimalFormat("0" + ("0".equals(ctx.getArgument()) ? "" :
//							"." + multiplyString("#", isInteger(ctx.getArgument()) ? Integer.parseInt(ctx.getArgument()) : 2))).format(ReachCommand.getReach(ctx.getPlayer(), false))));
//
//			// Example: %morecommands:gradient/#FFAA00-#FF0000;This text will be a gradient from orange to red.%, %morecommands:gradient/6-c;This text too will be a gradient from orange to red.%
//			PlaceholderAPI.register(new Identifier("morecommands:gradient"), MoreCommands::gradientPlaceholder);
//		}
	}

	static <T extends Command> List<Class<T>> getCommandClasses(String type, Class<T> clazz) {
		List<Class<T>> classes = new ArrayList<>();
		File jar = getJar();
		if (jar == null) {
			LOG.error("Could not find the jarfile of the mod, no commands will be registered.");
			return classes;
		}

		try {
			List<Path> classNames = new ArrayList<>();
			// It should only be a directory in the case of a dev environment, otherwise it should always be a jar file.
			if (jar.isDirectory())
				try (Stream<Path> files = Files.walk(new File(String.join(File.separator, jar.getAbsolutePath(), "com", "ptsmods", "morecommands", "commands", type, "")).toPath())) {
					classNames.addAll(files.filter(path -> Files.isRegularFile(path) && !path.getFileName().toString().contains("$")).collect(Collectors.toList()));
				}
			else {
				ZipFile zip = new ZipFile(jar);
				Enumeration<? extends ZipEntry> entries = zip.entries();

				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					if (entry.getName().startsWith("com/ptsmods/morecommands/commands/" + type + "/") && entry.getName().endsWith(".class") && !entry.getName().split("/")[entry.getName().split("/").length - 1].contains("$"))
						classNames.add(Paths.get(entry.getName()));
				}

				zip.close();
			}

			classNames.forEach(path -> {
				String name = "com.ptsmods.morecommands.commands." + type + ("server".equals(type) ? "." + path.toFile().getParentFile().getName() : "") + "." + path.getFileName().toString().substring(0, path.getFileName().toString().lastIndexOf('.'));
				try {
					Class<?> clazz0 = Class.forName(name);
					if (clazz.isAssignableFrom(clazz0)) classes.add(ReflectionHelper.cast(clazz0));
				} catch (Exception e) {
					LOG.error("Error loading class " + name, e);
				}
			});
			LOG.info("Found " + classes.size() + " commands to load for type " + type + ".");
		} catch (IOException e) {
			LOG.error("Couldn't find commands for type " + type + ". This means none of said type will be loaded.", e);
		}

		return classes;
	}

	@SuppressWarnings("deprecation") // Internal stuff, not API. Required to get this mod's location.
	static File getJar() {
		File jar;
		try {
			jar = new File(((ModContainer) FabricLoader.getInstance().getModContainer("morecommands").orElseThrow(NullPointerException::new)).getOriginUrl().toURI().getPath());
		} catch (URISyntaxException e) {
			LOG.catching(e);
			return null;
		}

		if (jar.isDirectory() && jar.getName().equals("main")) jar = new File(String.join(File.separator, jar.getParentFile().getParentFile().getAbsolutePath(), "classes", "java", "main", ""));
		return jar;
	}

	public static void setFormattings(Formatting def, Formatting sec) {
		if (def != null) {
			DF = Command.DF = def;
			DS = Command.DS = DS.withColor(DF);
		}

		if (sec != null) {
			SF = Command.SF = sec;
			SS = Command.SS = SS.withColor(SF);
		}
	}

	public static void updateFormatting(MinecraftServer server, int id, FormattingColour value) {
		if (IMoreCommands.get().isServerOnly()) return;

		switch (id) {
			case 0:
				value = value == null ? server.getGameRules().get(MoreGameRules.DFrule).get() : value;
				if (value.asFormatting() != DF) {
					setFormattings(value.asFormatting(), null);
					sendFormattingUpdate(server, 0, DF);
				}
				break;
			case 1:
				value = value == null ? server.getGameRules().get(MoreGameRules.SFrule).get() : value;
				if (value.asFormatting() != SF) {
					setFormattings(null, value.asFormatting());
					sendFormattingUpdate(server, 1, SF);
				}
				break;
		}
	}

	public static void sendFormattingUpdates(ServerPlayerEntity p) {
		if (IMoreCommands.get().isServerOnly()) return;
		sendFormattingUpdate(p, 0, DF);
		sendFormattingUpdate(p, 1, SF);
	}

	private static void sendFormattingUpdate(MinecraftServer server, int id, Formatting colour) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer().writeByte(id).writeByte(colour.getColorIndex()));
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
			sendFormattingUpdate(p, buf);
	}

	private static void sendFormattingUpdate(ServerPlayerEntity p, int id, Formatting colour) {
		sendFormattingUpdate(p, new PacketByteBuf(Unpooled.buffer().writeByte(id).writeByte(Arrays.binarySearch(FormattingColour.values(), FormattingColour.valueOf(colour.name())))));
	}

	private static void sendFormattingUpdate(ServerPlayerEntity p, PacketByteBuf buf) {
		/*if (ServerPlayNetworking.canSend(p, new Identifier("morecommands:formatting_update")))*/ ServerPlayNetworking.send(p, new Identifier("morecommands:formatting_update"), buf); // Bug still does not seem to be fixed.
	}

	static <T extends Command> T getInstance(Class<T> cmd) {
		Command instance;
		try {
			Constructor<T> con = cmd.getDeclaredConstructor();
			con.setAccessible(true);
			instance = con.newInstance();
		} catch (Exception e) {
			LOG.error("Could not instantiate command class " + cmd.getName() + ".", e);
			return null;
		}
		instance.setActiveInstance();
		try {
			instance.preinit(IMoreCommands.get().isServerOnly());
		} catch (Exception e) {
			LOG.error("Error invoking pre-initialisation method on class " + cmd.getName() + ".", e);
		}
		return ReflectionHelper.cast(instance);
	}

	public static String textToString(Text text, Style parentStyle, boolean includeFormattings) {
		return textToString(text, parentStyle, FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT, includeFormattings);
	}

	public static String textToString(Text text, Style parentStyle, boolean translate, boolean includeFormattings) {
		if (parentStyle == null) parentStyle = Style.EMPTY;

		TextBuilder<?> builder = CompatHolder.getCompat().builderFromText(text);
		StringBuilder s = new StringBuilder();
		Style style = text.getStyle();
		style = (style == null ? Style.EMPTY : style).withParent(parentStyle);

		if (includeFormattings) {
			TextColor c = style.getColor();

			if (c != null) {
				int rgb = ((MixinTextColorAccessor) (Object) c).getRgb_();
				Formatting f = null;

				for (Formatting form : Formatting.values())
					if (form.getColorValue() != null && form.getColorValue().equals(rgb)) {
						f = form;
						break;
					}

				if (f != null) s.append(f);
				else {
					Color colour = new Color(rgb);
					s.append("\u00A7").append(String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue()));
				}
			}

			if (style.isBold()) s.append(Formatting.BOLD);
			if (style.isStrikethrough()) s.append(Formatting.STRIKETHROUGH);
			if (style.isUnderlined()) s.append(Formatting.UNDERLINE);
			if (style.isItalic()) s.append(Formatting.ITALIC);
			if (style.isObfuscated()) s.append(Formatting.OBFUSCATED);
		}

		if (builder instanceof TranslatableTextBuilder && translate) {
			TranslatableTextBuilder tt = (TranslatableTextBuilder) builder; // TODO
			Object[] args = new Object[tt.getArgs().length];

			for (int i = 0; i < args.length; i++) {
				Object arg = tt.getArgs()[i];
				if (arg instanceof Text || arg instanceof TextBuilder)
					args[i] = textToString(arg instanceof Text ? (Text) arg : ((TextBuilder<?>) arg).build(), style, true, includeFormattings);
				else args[i] = arg;
			}

			s.append(I18n.translate(tt.getKey(), Arrays.stream(args)
					.map(o -> o instanceof TextBuilder ? ((TextBuilder<?>) o).build() : o)
					.collect(Collectors.toList())));
		} else s.append(builder instanceof LiteralTextBuilder ? ((LiteralTextBuilder) builder).getLiteral() :
				builder instanceof TranslatableTextBuilder ? ((TranslatableTextBuilder) builder).getKey() :
				text);

		if (!text.getSiblings().isEmpty())
			for (Text t : text.getSiblings())
				s.append(textToString(t, style, translate, includeFormattings));

		return s.toString();
	}

	public static String stripFormattings(String s) {
		return Objects.requireNonNull(Formatting.strip(s)).replaceAll("\u00A7#[0-9A-Fa-f]{6}", "");
	}

	public static void saveJson(File f, Object data) throws IOException {
		saveString(f, gson.toJson(data));
	}

	public static <T> T readJson(File f) throws IOException {
		return gson.fromJson(readString(f), new TypeToken<T>(){}.getType());
	}

	public static void saveString(File f, String s) throws IOException {
		createFileAndDirectories(f);
		try (PrintWriter writer = new PrintWriter(f, "UTF-8")) {
			writer.print(s);
			writer.flush();
		}
	}

	public static String readString(File f) throws IOException {
		createFileAndDirectories(f);
		return String.join("\n", Files.readAllLines(f.toPath()));
	}

	private static void createFileAndDirectories(File f) throws IOException {
		if (!f.getAbsoluteFile().getParentFile().exists() && !f.getAbsoluteFile().getParentFile().mkdirs()) throw new IOException("Could not create directories");
		if (!f.exists() && !f.createNewFile()) throw new IOException("Could not create file.");
	}

	public static char getChar(Formatting f) {
		return f.toString().charAt(1);
	}

	public static double factorial(double d) {
		double d0 = d - 1;
		while (d0 > 0)
			d *= d0--;
		return d;
	}

	/**
	 * Evaluates a math equation in a String. It does addition, subtraction,
	 * multiplication, division, exponentiation (using the ^ symbol), factorial (!
	 * <b>before</b> a number), and a few basic functions like sqrt. It supports
	 * grouping using (...), and it gets the operator precedence and associativity
	 * rules correct.
	 *
	 * @param str The equation to solve.
	 * @return The answer to the equation.
	 * @author Boann (<a href="https://stackoverflow.com/a/26227947">https://stackoverflow.com/a/26227947</a>)
	 */
	public static double eval(final String str) {
		return new Object() {
			int pos = -1, ch;

			void nextChar() {
				ch = ++pos < str.length() ? str.charAt(pos) : -1;
			}

			boolean eat(int charToEat) {
				while (ch == ' ')
					nextChar();
				if (ch == charToEat) {
					nextChar();
					return true;
				}
				return false;
			}

			double parse() {
				nextChar();
				double x = parseExpression();
				if (pos < str.length()) throw new RuntimeException("Unexpected character: " + (char) ch);
				return x;
			}

			double parseExpression() {
				double x = parseTerm();
				for (; ; )
					if (eat('+')) x += parseTerm(); // addition
					else if (eat('-')) x -= parseTerm(); // subtraction
					else return x;
			}

			double parseTerm() {
				double x = parseFactor();
				for (; ; )
					if (eat('*')) x *= parseFactor(); // multiplication
					else if (eat('/')) x /= parseFactor(); // division
					else return x;
			}

			double parseFactor() {
				if (eat('+')) return parseFactor(); // unary plus
				if (eat('-')) return -parseFactor(); // unary minus

				double x;
				int startPos = pos;
				if (eat('(')) { // parentheses
					x = parseExpression();
					eat(')');
				} else if (ch >= '0' && ch <= '9' || ch == '.') { // numbers
					while (ch >= '0' && ch <= '9' || ch == '.')
						nextChar();
					x = Double.parseDouble(str.substring(startPos, pos));
				} else if (ch >= 'a' && ch <= 'z' || ch == '!') { // functions
					while (ch >= 'a' && ch <= 'z' || ch == '!')
						nextChar();
					String func = str.substring(startPos, pos);
					x = parseFactor();
					switch (func) {
						case "sqrt":
							x = Math.sqrt(x);
							break;
						case "cbrt":
							x = Math.cbrt(x);
							break;
						case "sin":
							x = Math.sin(Math.toRadians(x));
							break;
						case "cos":
							x = Math.cos(Math.toRadians(x));
							break;
						case "tan":
							x = Math.tan(Math.toRadians(x));
							break;
						case "pi":
							x = Math.PI * (x == 0D ? 1D : x);
							break;
						case "!":
							x = factorial(x);
							break;
						default:
							throw new RuntimeException("Unknown function: " + func);
					}
				} else if (ch != -1) throw new RuntimeException("Unexpected character: " + (char) ch);
				else x = 0D;

				if (eat('^')) x = Math.pow(x, parseFactor()); // exponentiation
				return x;
			}
		}.parse();
	}

	// Blatantly copied from GameRenderer, can raytrace both entities and blocks.
	public static HitResult getRayTraceTarget(Entity entity, World world, double reach, boolean ignoreEntities, boolean ignoreLiquids) {
		HitResult crosshairTarget = null;
		if (entity != null && world != null) {
			float td = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? MinecraftClient.getInstance().getTickDelta() : 1f;
			crosshairTarget = entity.raycast(reach, td, !ignoreLiquids);
			if (!ignoreEntities) {
				Vec3d vec3d = entity.getCameraPosVec(td);
				double e = reach;
				e *= e;
				if (crosshairTarget != null)
					e = crosshairTarget.getPos().squaredDistanceTo(vec3d);
				Vec3d vec3d2 = entity.getRotationVec(td);
				Vec3d vec3d3 = vec3d.add(vec3d2.x * reach, vec3d2.y * reach, vec3d2.z * reach);
				Box box = entity.getBoundingBox().stretch(vec3d2.multiply(reach)).expand(1.0D, 1.0D, 1.0D);
				EntityHitResult entityHitResult = ProjectileUtil.raycast(entity, vec3d, vec3d3, box, (entityx) -> !entityx.isSpectator() && entityx.collides(), e);
				if (entityHitResult != null) crosshairTarget = entityHitResult;
			}
		}
		return crosshairTarget;
	}

	public static Entity cloneEntity(Entity entity, boolean summon) {
		NbtCompound nbt = new NbtCompound();
		entity.saveSelfNbt(nbt);
		Entity e = EntityType.loadEntityWithPassengers(nbt, entity.getEntityWorld(), e0 -> {
			e0.refreshPositionAndAngles(entity.getX(), entity.getY(), entity.getZ(), ((MixinEntityAccessor) entity).getYaw_(), ((MixinEntityAccessor) entity).getPitch_());
			return e0;
		});
		if (e != null) {
			e.setUuid(UUID.randomUUID());
			if (summon) entity.getEntityWorld().spawnEntity(e);
		}
		return e;
	}

	public static String readTillSpaceOrEnd(StringReader reader) {
		StringBuilder s = new StringBuilder();
		while (reader.getRemainingLength() > 0 && reader.peek() != ' ')
			s.append(reader.read());
		return s.toString();
	}

	public static void teleport(Entity target, ServerWorld world, Vec3d pos, float yaw, float pitch) {
		teleport(target, world, pos.x, pos.y, pos.z, yaw, pitch);
	}

	// Blatantly copied from TeleportCommand#teleport.
	public static void teleport(Entity target, ServerWorld world, double x, double y, double z, float yaw, float pitch) {
		if (target instanceof ServerPlayerEntity) {
			ChunkPos chunkPos = new ChunkPos(new BlockPos(x, y, z));
			world.getChunkManager().addTicket(ChunkTicketType.POST_TELEPORT, chunkPos, 1, target.getId());
			target.stopRiding();
			if (((ServerPlayerEntity) target).isSleeping()) ((ServerPlayerEntity) target).wakeUp(true, true);
			if (world == target.world)
				((ServerPlayerEntity) target).networkHandler.requestTeleport(x, y, z, yaw, pitch, Collections.emptySet());
			else ((ServerPlayerEntity) target).teleport(world, x, y, z, yaw, pitch);
			target.setHeadYaw(yaw);
		} else {
			float f = MathHelper.wrapDegrees(yaw);
			float g = MathHelper.wrapDegrees(pitch);
			g = MathHelper.clamp(g, -90.0F, 90.0F);
			if (world == target.world) {
				target.refreshPositionAndAngles(x, y, z, f, g);
				target.setHeadYaw(f);
			} else {
				target.detach();
				Entity entity = target;
				target = target.getType().create(world);
				if (target == null) return;
				target.copyFrom(entity);
				target.refreshPositionAndAngles(x, y, z, f, g);
				target.setHeadYaw(f);
				world.onDimensionChanged(target);
				CompatHolder.getCompat().setRemoved(entity, 4); // CHANGED_DIMENSION
			}
		}
		if (!(target instanceof LivingEntity) || !((LivingEntity) target).isFallFlying()) {
			target.setVelocity(target.getVelocity().multiply(1.0D, 0.0D, 1.0D));
			target.setOnGround(true);
		}
		if (target instanceof PathAwareEntity) ((PathAwareEntity) target).getNavigation().stop();
	}

	public static <T> void removeNode(CommandDispatcher<T> dispatcher, CommandNode<T> child) {
		removeNode(dispatcher.getRoot(), child);
	}

	public static <S> void removeNode(CommandNode<S> parent, CommandNode<S> child) {
		if (parent.getChildren().contains(child)) {
			Objects.requireNonNull(ReflectionHelper.<Map<String, CommandNode<S>>, Object>getFieldValue(CommandNode.class, "children", parent)).remove(child.getName());
			if (child instanceof LiteralCommandNode)
				Objects.requireNonNull(ReflectionHelper.<Map<String, LiteralCommandNode<S>>, Object>getFieldValue(CommandNode.class, "literals", parent)).remove(child.getName());
			else if (child instanceof ArgumentCommandNode)
				Objects.requireNonNull(ReflectionHelper.<Map<String, ArgumentCommandNode<S, ?>>, Object>getFieldValue(CommandNode.class, "arguments", parent)).remove(child.getName());
		}
	}

	public static String getHTML(String url) throws IOException {
		URL url0 = new URL(url);
		URLConnection connection = url0.openConnection();
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36 OPR/81.0.4196.61");
		return readInputStream(connection.getInputStream());
	}

	public static String readInputStream(InputStream stream) throws IOException {
		try (BufferedReader rd = new BufferedReader(new InputStreamReader(stream))) {
			return rd.lines().collect(Collectors.joining("\n"));
		}
	}

	public static void setServerInstance(MinecraftServer server) {
		serverInstance = server;
		Command.doInitialisations(server);

		File file = new File("config/MoreCommands/SERVERONLY.txt");

		try {
			if (server.isDedicated() && (!file.exists() || Files.readAllLines(file.toPath()).isEmpty())) {
				Properties props = new Properties();
				props.setProperty("serverOnly", "" + file.exists());

				props.store(new PrintWriter(file), "Set the below value to true to enable server-only mode for MoreCommands.\n" +
						"Clients will not need the mod to join the server when enabled.");
			}
		} catch (IOException e) {
			LOG.catching(e);
		}

		MoreGameRules.checkPerms(server);
		LOG.info("MoreCommands data path: " + getRelativePath(server));
	}

	public static boolean isAprilFirst() {
		return Calendar.getInstance().get(Calendar.MONTH) == Calendar.APRIL && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1;
	}

	public static boolean isInteger(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static boolean isInteger(String s, int base) {
		try {
			Integer.parseInt(s, base);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static String translateFormattings(String s) {
		for (Formatting f : Formatting.values())
			s = s.replaceAll("&" + f.toString().charAt(1), f.toString());
		return s;
	}

	public static boolean inRange(double d, double min, double max) {
		return d >= min && d <= max;
	}

	public static String getLookDirection(float yaw, float pitch) {
		String direction = "unknown";
		if (pitch <= -30) direction = "up";
		else if (pitch >= 30) direction = "down";
		else if (inRange(yaw, 0, 22.5D) || inRange(yaw, -22.5D, 0)) direction = "south";
		else if (inRange(yaw, 22.5D, 67.5D)) direction = "south-west";
		else if (inRange(yaw, 67.5D, 112.5D)) direction = "west";
		else if (inRange(yaw, 112.5D, 157.5D)) direction = "north-west";
		else if (inRange(yaw, 157.5D, 180D) || inRange(yaw, -180D, -157.5D)) direction = "north";
		else if (inRange(yaw, -157.5D, -112.5D)) direction = "north-east";
		else if (inRange(yaw, -112.5D, -67.5D)) direction = "east";
		else if (inRange(yaw, -67.5D, -22.5D)) direction = "south-east";
		return direction;
	}

	public static String parseTime(long gameTime, boolean muricanClock) {
		long hours = gameTime / 1000 + 6;
		long minutes = gameTime % 1000 * 60 / 1000;
		String ampm = "AM";
		if (muricanClock) {
			if (hours > 12) {
				hours -= 12;
				ampm = "PM";
			}
			if (hours > 12) {
				hours -= 12;
				ampm = "AM";
			}
			if (hours == 0) hours = 12;
		} else if (hours >= 24) hours -= 24;
		String mm = "0" + minutes;
		mm = mm.substring(mm.length() - 2);
		return hours + ":" + mm + (muricanClock ? " " + ampm : "");
	}

	public static String formatDouble(Double dbl) {
		String dblString = dbl.toString();
		String dblString1 = dblString.split("\\.").length == 2 ? dblString.split("\\.")[1] : "";
		while (dblString1.endsWith("0"))
			dblString1 = dblString1.substring(0, dblString1.length() - 1);
		dblString1 = dblString1.length() >= 5 ? dblString1.substring(0, 5) : dblString1;
		if (dblString1.equals("")) dblString = dblString.split("\\.")[0];
		else dblString = dblString.split("\\.")[0] + "." + dblString1;
		return dblString;
	}

	public static <S> LiteralCommandNode<S> createAlias(String alias, LiteralCommandNode<S> node) {
		LiteralCommandNode<S> node0 = new LiteralCommandNode<>(alias, node.getCommand(), node.getRequirement(), node, ctx -> Collections.singletonList(ctx.getSource()), false);
		node.getChildren().forEach(node0::addChild);
		return node0;
	}

	public static void execute(Runnable runnable) {
		executor.execute(runnable);
	}

	public static String formatFileSize(long bytes) {
		double size = bytes;
		int count = 0;
		while (size >= 1024 && count < 4) {
			size /= 1024F;
			count += 1;
		}
		String s = sizeFormat.format(size);
		switch (count) {
			case 0:
				return s + " bytes";
			case 1:
				return s + " kilobytes";
			case 2:
				return s + " megabytes";
			case 3:
				return s + " gigabytes";
			default:
				return s + " terabytes";
		}
	}

	// Me
	public static boolean isCool(Entity entity) {
		return entity instanceof PlayerEntity && "1aa35f31-0881-4959-bd14-21e8a72ba0c1".equals(entity.getUuidAsString());
	}

	// My best friend :3
	public static boolean isCute(Entity entity) {
		return entity instanceof PlayerEntity && "b8760dc9-19fd-4d01-a5c7-25268a677deb".equals(entity.getUuidAsString());
	}

	public static NbtCompound getDefaultTag(EntityType<?> type) {
		NbtCompound tag = new NbtCompound();
		tag.putString("id", Registry.ENTITY_TYPE.getId(type).toString());
		return tag;
	}

	@SuppressWarnings("UnusedReturnValue")
	public static Entity summon(NbtCompound tag, ServerWorld world, Vec3d pos) {
		return EntityType.loadEntityWithPassengers(tag, world, (entityx) -> {
			entityx.refreshPositionAndAngles(pos.x, pos.y, pos.z, ((MixinEntityAccessor) entityx).getYaw_(), ((MixinEntityAccessor) entityx).getPitch_());
			return !world.tryLoadEntity(entityx) ? null : entityx;
		});
	}

	public static Vec3d getRotationVector(Vec2f rotation) {
		return getRotationVector(rotation.x, rotation.y);
	}

	public static Vec3d getRotationVector(float pitch, float yaw) {
		float pitchRad = pitch * 0.017453292F;
		float yawRad = -yaw * 0.017453292F;
		float h = MathHelper.cos(yawRad);
		float i = MathHelper.sin(yawRad);
		float j = MathHelper.cos(pitchRad);
		float k = MathHelper.sin(pitchRad);
		return new Vec3d(i * j, -k, h * j);
	}

	public static boolean teleportSafely(Entity entity) {
		World world = entity.getEntityWorld();
		double x = entity.getPos().x;
		double y;
		double z = entity.getPos().z;
		boolean found = false;
		boolean blockAbove = world.isSkyVisible(entity.getBlockPos());
		if (!world.isClient) while (!found && !blockAbove) {
			for (y = entity.getPos().y + 1; y < entity.getEntityWorld().getHeight(); y += 1) {
				Block block = world.getBlockState(new BlockPos(x, y - 1, z)).getBlock();
				Block tpblock = world.getBlockState(new BlockPos(x, y, z)).getBlock();
				if (!blockBlacklist.contains(block) && blockWhitelist.contains(tpblock) && (blockAbove = world.isSkyVisible(new BlockPos(x, y, z)))) {
					entity.updatePosition(x + 0.5, y, z + 0.5);
					found = true;
					break;
				}
			}
			x -= 1;
			z -= 1;
		}
		return !blockAbove;
	}

	// Copied from SpreadPlayersCommand$Pile#getY(BlockView, int)
	public static int getY(BlockView blockView, double x, double z) {
		BlockPos.Mutable mutable = new BlockPos.Mutable(x, CompatHolder.getCompat().getWorldHeight(blockView), z);
		boolean bl = blockView.getBlockState(mutable).isAir();
		mutable.move(Direction.DOWN);
		boolean bl3;
		for (boolean bl2 = blockView.getBlockState(mutable).isAir(); mutable.getY() > 0; bl2 = bl3) {
			mutable.move(Direction.DOWN);
			bl3 = blockView.getBlockState(mutable).isAir();
			if (!bl3 && bl2 && bl) return mutable.getY() + 1;
			bl = bl2;
		}
		return blockView.getHeight();
	}

	public static NbtCompound wrapTag(String key, NbtElement tag) {
		NbtCompound compound = new NbtCompound();
		compound.put(key, tag);
		return compound;
	}

	public static String pascalCase(String s, boolean retainSpaces) {
		String[] parts = s.split(" ");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.length() == 1) sb.append(part.toUpperCase());
			else sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
			if (retainSpaces) sb.append(' ');
		}
		return sb.toString().trim();
	}

	public static VoxelShape getFluidShape(BlockState state) {
		return VoxelShapes.cuboid(0, 0, 0, 1, 1d/1.125d/8 * (8-state.get(FluidBlock.LEVEL)), 1);
	}

	public static String getRelativePath() {
		return getRelativePath(serverInstance);
	}
	
	public static String getRelativePath(MinecraftServer server) {
		return server.getSavePath(WorldSavePath.ROOT).toAbsolutePath() + File.separator + "MoreCommands" + File.separator;
	}

	public static void tryMove(String from, String to) {
		try {
			Files.move(Paths.get(from), Paths.get(to));
		} catch (IOException e) {
			LOG.catching(e);
		}
	}

	public static boolean isSingleplayer() {
		return FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER && MinecraftClient.getInstance() != null &&
				MinecraftClient.getInstance().getCurrentServerEntry() == null && MinecraftClient.getInstance().world != null;
	}

	public static String formatSeconds(long seconds, Formatting mainColour, Formatting commaColour) {
		long days = seconds / 86400;
		long hours = seconds / 3600 - days * 24;
		long minutes = seconds / 60 - hours * 60 - days * 1440;
		seconds = seconds % 60;
		StringBuilder sb = new StringBuilder(mainColour.toString());
		if (days > 0) sb.append(days).append(" day").append(days == 1 ? "" : "s");
		if (hours > 0) sb.append(sb.length() == 2 ? "" : commaColour + ", " + mainColour).append(hours).append(" hour").append(hours == 1 ? "" : "s");
		if (minutes > 0) sb.append(sb.length() == 2 ? "" : commaColour + ", " + mainColour).append(minutes).append(" minute").append(minutes == 1 ? "" : "s");
		if (seconds > 0) sb.append(sb.length() == 2 ? "" : commaColour + ", " + mainColour).append(seconds).append(" seconds").append(seconds == 1 ? "" : "s");
		String s = sb.toString();
		if (s.contains(",")) s = s.substring(0, s.lastIndexOf(',')) + " and" + s.substring(s.lastIndexOf(',') + 1);
		return s;
	}

	public boolean isServerOnly() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER && SERVER_ONLY;
	}

	// Following 2 methods are from Apache Commons Codec.
	public static String encodeHex(byte[] data) {
		char[] out = new char[data.length << 1];

		for (int i = 0, j = 0; i < data.length; i++) {
			out[j++] = HEX_DIGITS[(0xF0 & data[i]) >>> 4];
			out[j++] = HEX_DIGITS[0x0F & data[i]];
		}

		return new String(out);
	}

	public static byte[] decodeHex(String data) {
		char[] dataChars = data.toCharArray();
		byte[] out = new byte[dataChars.length >> 1];

		for (int i = 0, j = 0; j < dataChars.length; i++) {
			int f = Character.digit(dataChars[j], 16) << 4;
			j++;
			f = f | Character.digit(dataChars[j], 16);
			j++;
			out[i] = (byte) (f & 0xFF);
		}

		return out;
	}

	public static String multiplyString(String s, int x) {
		if (s == null || s.isEmpty()) return s;

		StringBuilder result = new StringBuilder();
		for (int i = 0; i < x; i++)
			result.append(s);

		return result.toString();
	}

	public static List<Color> createGradient(int steps, Color from, Color to) {
		if (steps == 0) return Lists.newArrayList();
		BufferedImage img = new BufferedImage(steps, 1, BufferedImage.TYPE_INT_RGB);

		Graphics2D g = img.createGraphics();
		g.setPaint(new GradientPaint(0, 0, from, steps, 1, to));
		g.fillRect(0, 0, steps, 1);
		g.dispose();

		List<Color> gradient = new ArrayList<>();
		for (int i = 0; i < steps; i++)
			gradient.add(new Color(img.getRGB(i, 0)));
		return Collections.unmodifiableList(gradient);
	}

	public static Text getNickname(PlayerEntity player) {
		 Text nickname = player.getDataTracker().get(DataTrackerHelper.NICKNAME).orElse(null);
		 if (nickname == null) return null;

		 Identifier id = new Identifier("morecommands:gradient");
		 return nickname;
	}

//	public static PlaceholderResult gradientPlaceholder(PlaceholderContext ctx) {
//		String arg = ctx.getArgument();
//		String[] parts = arg.split(";", 2);
//
//		if (arg.isEmpty() || parts.length != 2 || !parts[0].contains("-")) return PlaceholderResult.value("Invalid format. Valid example: %morecommands:gradient/#FFAA00-#FF0000;Text here%");
//
//		String fromS = parts[0].substring(0, parts[0].indexOf('-'));
//		String toS = parts[0].substring(parts[0].indexOf('-') + 1, parts[0].contains(",") ? parts[0].indexOf(',') : parts[0].length());
//		String formatting = parts[0].contains(",") ? parts[0].substring(parts[0].indexOf(',') + 1) : "";
//
//		char[] chs = formatting.toCharArray();
//		Character[] chsI = new Character[chs.length];
//
//		for (int i = 0; i < chs.length; i++)
//			chsI[i] = chs[i];
//
//		// Turns any formatting, like lm or lo, into their actual string representation.
//		formatting = Arrays.stream(chsI)
//				.map(c -> "" + Character.toLowerCase(c))
//				.filter("klmno"::contains)
//				.map(c -> "\u00A7" + c)
//				.collect(Collectors.joining());
//
//		Function<String, Boolean> checkColour = c -> c.length() == 1 ? Character.isDigit(c.charAt(0)) || Character.toLowerCase(c.charAt(0)) >= 'a' && Character.toLowerCase(c.charAt(0)) <= 'f' :
//				c.length() == 7 && c.charAt(0) == '#' && isInteger(c.substring(1), 16);
//
//		// Checking if the colours are actual colours.
//		if (!checkColour.apply(fromS))
//			return PlaceholderResult.value("First colour must be between 0-9 or a-f or a hex colour.");
//		if (!checkColour.apply(toS))
//			return PlaceholderResult.value("Second colour must be between 0-9 or a-f or a hex colour.");
//
//		// Parse colours and create gradient.
//		Color from = new Color(fromS.length() == 1 ? Objects.requireNonNull(TextColor.fromFormatting(Formatting.byCode(fromS.charAt(0)))).getRgb() : Integer.parseInt(fromS.substring(1), 16));
//		Color to = new Color(toS.length() == 1 ? Objects.requireNonNull(TextColor.fromFormatting(Formatting.byCode(toS.charAt(0)))).getRgb() : Integer.parseInt(toS.substring(1), 16));
//		String content = parts[1];
//		List<Color> gradient = createGradient(content.length(), from, to);
//
//		StringBuilder result = new StringBuilder();
//		for (int i = 0; i < content.length(); i++) {
//			Color c = gradient.get(i);
//
//			result.append("\u00a7#")
//					.append(String.format("%02x", c.getRed()))
//					.append(String.format("%02x", c.getGreen()))
//					.append(String.format("%02x", c.getBlue()))
//					.append(formatting)
//					.append(content.charAt(i));
//		}
//
//		return PlaceholderResult.value(result.toString());
//	}

	public static int getMoonPhase(long time) {
		return (int)(time / 24000L % 8L + 8L) % 8;
	}
}