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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;


@SuppressWarnings("ALL")
public class DapSanity implements ModInitializer {

	public static ServerPlayer p1Dapping;
	public static ServerPlayer p2Dapping;
	public static int tierOfDap;


	public static final String MOD_ID = "dapsanity";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static int maxSanity = 100;
	public static int baseTicksBetweenSanityDrops = 20;

	public static final AttachmentType<Integer> Sanity_Attachment = AttachmentRegistry.create(
			id("sanity"),
			builder -> builder
					.persistent(Codec.INT)
					.initializer(() -> maxSanity)
					.syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.targetOnly())
	);

	public static final AttachmentType<Integer> Prev_Sanity_Attachment = AttachmentRegistry.create(
			id("prev_sanity"),
			builder -> builder
					.persistent(Codec.INT)
					.initializer(() -> maxSanity)
					.syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.targetOnly())
	);

	public static final AttachmentType<Integer> Ticks_Since_Last_Drop_Attachment = AttachmentRegistry.create(
			id("ticks_since_last_drop"),
			builder -> builder
					.persistent(Codec.INT)
					.initializer(() -> 0)
					.syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.targetOnly())
	);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}


	@Override
	public void onInitialize() {

		ServerTickEvents.START_SERVER_TICK.register(server -> {

			for (ServerPlayer player : PlayerLookup.all(server)) {
				//player.setAttached(DapSanity.Prev_Sanity_Attachment, DebugWhenNotEqual(player.getAttachedOrCreate(DapSanity.Prev_Sanity_Attachment), player.getAttachedOrCreate(DapSanity.Sanity_Attachment), player));
				handleDropInterval(player);
			}

			//Increase sanity on dap depending on tier
			if(p1Dapping != null && p2Dapping != null && tierOfDap != -1){
				int p1NewSanity = p1Dapping.getAttachedOrCreate(Sanity_Attachment) + sanityIncrease(tierOfDap) <101 ? p1Dapping.getAttachedOrCreate(Sanity_Attachment) + sanityIncrease(tierOfDap) : 100;
				int p2NewSanity = p2Dapping.getAttachedOrCreate(Sanity_Attachment) + sanityIncrease(tierOfDap) <101 ? p2Dapping.getAttachedOrCreate(Sanity_Attachment) + sanityIncrease(tierOfDap) : 100;
				p1Dapping.setAttached(Sanity_Attachment, p1NewSanity);
				p2Dapping.setAttached(Sanity_Attachment, p2NewSanity);

				p1Dapping = null;
				p2Dapping = null;
				tierOfDap = -1;
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

	public int sanityIncrease(int tier){
		if (tier == 0){
			return 10;
		}
		else if(tier == 1){
			return 15;
		}
		else if(tier == 2){
			return 20;
		}
		else if(tier == 3){
			return 25;
		}
		else if(tier == 4){
			return 50;
		}
		else if(tier == 5){
			return 100;
		}
		else {
			return 0;
		}
	}

	private static int SanitySetCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		int value = IntegerArgumentType.getInteger(context, "value");

		for (ServerPlayer p : EntityArgument.getPlayers(context, "player")) {
			p.setAttached(Sanity_Attachment, value);
		}

		context.getSource().sendSuccess(
				() -> Component.literal("Set " + target.getName().getString() + "'s sanity to " + value),
				true
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
		int ticksBetweenSanityDrops = Math.toIntExact(Math.round(((16.0 / 3.0) * GetRawLight(player) + 40)));
		if (ticksBetweenSanityDrops < player.getAttachedOrCreate(Ticks_Since_Last_Drop_Attachment) && !player.isCreative()){
			if (player.getAttachedOrCreate(Sanity_Attachment) > 0){
				int newSanity = player.getAttachedOrCreate(Sanity_Attachment) - 1;
				player.setAttached(Sanity_Attachment, newSanity);
			}
			else {
				MobEffectInstance witherEffect = new MobEffectInstance(
						MobEffects.WITHER,
						140,
						7
				);
                player.addEffect(witherEffect);
			}
			player.setAttached(Ticks_Since_Last_Drop_Attachment, 0);
			HandleSanityWarnings(player.getAttachedOrCreate(Sanity_Attachment), player);
		}
	}

	public static void HandleSanityWarnings(int sanity, ServerPlayer player){
		MobEffectInstance blindnessEffect = new MobEffectInstance(
				MobEffects.BLINDNESS,
				160,
				255
		);
		if(sanity == 50){
			//heartbeat
			playSoundFor(player, sanityLow, 0.5f);
		}
		else if(sanity == 25){
            //heartbeat, blindness, breath
			playSoundFor(player, sanityLow, 1.0f);
			playSoundFor(player, sanityReallyLow, 0.25f);
			player.addEffect(blindnessEffect);
		}
		else if(sanity<11){
			//warning 3 blindness, heartbeat
			playSoundFor(player, sanityLow, 1.0f);
			player.addEffect(blindnessEffect);
		}
	}

	public static final SoundEvent sanityLow = registerSound("heartbeat");
	public static final SoundEvent sanityReallyLow = registerSound("short_scary_sound");

	private static SoundEvent registerSound(String name) {
		Identifier id = id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void playSoundFor(ServerPlayer player, SoundEvent sound, float volume) {
		player.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
				SoundSource.MASTER,
				player.getX(), player.getY(), player.getZ(),
				volume,
				1.0f,   // pitch
				player.getRandom().nextLong() // seed
		));
	}

	/*
import net.minecraft.resources.Identifier;
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
	*/
}
