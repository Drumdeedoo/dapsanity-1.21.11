package drumdeedoo.dapsanity.net;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DapSanity implements ModInitializer {

	public static final String MOD_ID = "dapsanity";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ServerPlayer player;
	public static int maxSanity = 100;
	public static int prevSanity;
	public static int sanity;
	public static int baseTicksBetweenSanityDrops = 20;
	public static int ticksBetweenSanityDrops;
	public static int ticksSinceLastDrop;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in b mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			player = handler.player;
			// Handle player join here

			sanity = maxSanity;

			player.sendSystemMessage(Component.literal("Hello, internet!"));
		});

		ServerTickEvents.START_SERVER_TICK.register(server -> {
			prevSanity = DebugWhenNotEqual(prevSanity, sanity);
			handleDropInterval();
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("dapsanity")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(Commands.literal("sanity")
					.then(Commands.literal("set")
						.then(Commands.argument("value", IntegerArgumentType.integer())
							.executes(DapSanity::SanitySetCommand)))));
		});

    }

	private static int SanitySetCommand(CommandContext<CommandSourceStack> context) {
		sanity = IntegerArgumentType.getInteger(context, "value");
		return 1;
	}

	public static int DebugWhenNotEqual(int a, int b){
		if(a != b){
			player.sendSystemMessage(Component.literal(String.format("New Value: %s", Integer.toString(b))));
			return b;
		}
		return a;
	}

	public static int GetRawLight(){
		BlockPos posBelow = player.blockPosition();
		int skyDarkness = player.level().getSkyDarken();
		return Math.max(
				player.level().getBrightness(LightLayer.BLOCK, posBelow),
				player.level().getBrightness(LightLayer.SKY, posBelow) - skyDarkness
		);
	}

	public static void handleDropInterval(){
		ticksSinceLastDrop++;
		ticksBetweenSanityDrops = Math.toIntExact(Math.round(baseTicksBetweenSanityDrops * Math.max(1, (0.4 * GetRawLight()))));
		if (ticksBetweenSanityDrops < ticksSinceLastDrop && !player.isCreative()){
			if (sanity > 0){
				sanity--;
			}
			else {
				MobEffectInstance witherEffect = new MobEffectInstance(
						MobEffects.WITHER, // The effect registry entry
						140,               // Duration in ticks (e.g., 10 seconds)
						10                // Amplifier (0 = Level I, 1 = Level II)
				);
                player.addEffect(witherEffect);
			}
			ticksSinceLastDrop = 0;
			HandleSanityWarnings(sanity);
		}
	}

	public static void HandleSanityWarnings(int sanity){
		MobEffectInstance blindnessEffect = new MobEffectInstance(
				MobEffects.BLINDNESS, // The effect registry entry
				140,               // Duration in ticks (e.g., 10 seconds)
				0                  // Amplifier (0 = Level I, 1 = Level II)
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

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
