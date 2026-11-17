package drumdeedoo.dapsanity.net;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SuppressWarnings("ALL")
public class DapSanity implements ModInitializer {

	public static final String MOD_ID = "dapsanity";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static int maxSanity = 100;
	public static int baseTicksBetweenSanityDrops = 20;

	public static final AttachmentType<Integer> Sanity_Attachment = AttachmentRegistry.create(
			id("sanity"),
			builder -> builder
					.persistent(Codec.INT)                 // saved with the player
					.initializer(() -> maxSanity)         // default for players with no data yet
					.syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.targetOnly()) // send to that player's client
	);

	public static final AttachmentType<Integer> Prev_Sanity_Attachment = AttachmentRegistry.create(
			id("prev_sanity"),
			builder -> builder
					.persistent(Codec.INT)                 // saved with the player
					.initializer(() -> maxSanity)         // default for players with no data yet
					.syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.targetOnly()) // send to that player's client
	);

	public static final AttachmentType<Integer> Ticks_Since_Last_Drop_Attachment = AttachmentRegistry.create(
			id("ticks_since_last_drop"),
			builder -> builder
					.persistent(Codec.INT)                 // saved with the player
					.initializer(() -> 0)         // default for players with no data yet
					.syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.targetOnly()) // send to that player's client
	);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}


	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in b mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {

		});

		ServerTickEvents.START_SERVER_TICK.register(server -> {

			for (ServerPlayer player : PlayerLookup.all(server)) {
				player.setAttached(DapSanity.Prev_Sanity_Attachment, DebugWhenNotEqual(player.getAttachedOrCreate(DapSanity.Prev_Sanity_Attachment), player.getAttachedOrCreate(DapSanity.Sanity_Attachment), player));
				handleDropInterval(player);
			}

		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("dapsanity")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            	.then(Commands.literal("sanity")
                	.then(Commands.literal("set")
						.then(Commands.argument("player", EntityArgument.players())
                    		.then(Commands.argument("value", IntegerArgumentType.integer(0, maxSanity))
                        		.executes(DapSanity::SanitySetCommand)))))));

    }

	private static int SanitySetCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		int value = IntegerArgumentType.getInteger(context, "value");

		for (ServerPlayer p : EntityArgument.getPlayers(context, "player")) {
			p.setAttached(Sanity_Attachment, value);
		}

		context.getSource().sendSuccess(
				() -> Component.literal("Set " + target.getName().getString() + "'s sanity to " + value),
				true // broadcast to other ops
		);
		return 1;
	}

	public static int DebugWhenNotEqual(int a, int b, ServerPlayer player){
		if(a != b){
			player.sendSystemMessage(Component.literal(String.format("New Value: %s", b)));
			return b;
		}
		return a;
	}

	public static int GetRawLight(ServerPlayer player){
		BlockPos posBelow = player.blockPosition();
		int skyDarkness = player.level().getSkyDarken();
		return Math.max(
				player.level().getBrightness(LightLayer.BLOCK, posBelow),
				player.level().getBrightness(LightLayer.SKY, posBelow) - skyDarkness
		);
	}

	public static void handleDropInterval(ServerPlayer player){
		int newTicks = player.getAttachedOrCreate(Ticks_Since_Last_Drop_Attachment) + 1;
		player.setAttached(Ticks_Since_Last_Drop_Attachment, newTicks);
		int ticksBetweenSanityDrops = Math.toIntExact(Math.round(baseTicksBetweenSanityDrops * Math.max(1, (0.4 * GetRawLight(player)))));
		if (ticksBetweenSanityDrops < player.getAttachedOrCreate(Ticks_Since_Last_Drop_Attachment) && !player.isCreative()){
			if (player.getAttachedOrCreate(Sanity_Attachment) > 0){
				int newSanity = player.getAttachedOrCreate(Sanity_Attachment) - 1;
				player.setAttached(Sanity_Attachment, newSanity);
			}
			else {
				MobEffectInstance witherEffect = new MobEffectInstance(
						MobEffects.WITHER, // The effect registry entry
						140,               // Duration in ticks (e.g., 10 seconds)
						7                // Amplifier (0 = Level I, 1 = Level II)
				);
                player.addEffect(witherEffect);
			}
			player.setAttached(Ticks_Since_Last_Drop_Attachment, 0);
			HandleSanityWarnings(player.getAttachedOrCreate(Sanity_Attachment), player);
		}
	}

	public static void HandleSanityWarnings(int sanity, ServerPlayer player){
		MobEffectInstance blindnessEffect = new MobEffectInstance(
				MobEffects.BLINDNESS, // The effect registry entry
				160,               // Duration in ticks (e.g., 10 seconds)
				255                  // Amplifier (0 = Level I, 1 = Level II)
		);
		if(sanity == 50){
			//heartbeat
			player.sendSystemMessage(Component.literal("Thump Thump"));
		}
		else if(sanity == 25){
            //heartbeat, blindness, breath
			player.sendSystemMessage(Component.literal("Thump Thump"));
			player.sendSystemMessage(Component.literal("hhhhHHHHHHHH"));
			player.addEffect(blindnessEffect);
		}
		else if(sanity<11){
			//warning 3 blindness + eyes, whispers, heartbeat, ?vhs?
			player.sendSystemMessage(Component.literal("Thump Thump"));
			player.sendSystemMessage(Component.literal("*Unintelligible whispering*"));
			player.addEffect(blindnessEffect);
		}
	}

	/*
import net.minecraft.resources.Identifier;
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
	*/
}
