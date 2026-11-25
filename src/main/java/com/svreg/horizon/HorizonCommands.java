package com.svreg.horizon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * /horizon                 opens the menu
 * /horizon info            the map's state in chat (where its data comes from, how far the generator got, how fast)
 * /horizon jarak <blok>    how far the world generator fills the map (256 .. 2048 blocks)
 * /horizon generator on|off
 * /horizon tes on|off     option 3, stage 1: a made-up hill drawn by the mod in 3D, far away (test only)
 */
public final class HorizonCommands {
	private HorizonCommands() {
	}

	private static void say(FabricClientCommandSource src, String msg) {
		src.sendFeedback(Component.literal("§b[SvregHorizon]§r " + msg));
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("horizon")
						.executes(c -> {
							HorizonScreen.openSoon(); // the menu
							return 1;
						})
						.then(ClientCommandManager.literal("info").executes(c -> {
							for (String line : HorizonWorker.status()) {
								say(c.getSource(), line);
							}
							return 1;
						}))
						.then(ClientCommandManager.literal("jarak")
								.executes(c -> {
									say(c.getSource(), "jarak generator sekarang " + HorizonConfig.radius + " blok (ubah: /horizon jarak <" + HorizonConfig.MIN_RADIUS + "-" + HorizonConfig.MAX_RADIUS + ">)");
									return 1;
								})
								.then(ClientCommandManager.argument("blok", IntegerArgumentType.integer(HorizonConfig.MIN_RADIUS, HorizonConfig.MAX_RADIUS))
										.executes(c -> {
											HorizonConfig.radius = HorizonConfig.clamp(IntegerArgumentType.getInteger(c, "blok"));
											HorizonConfig.save();
											HorizonWorker.settingsChanged();
											say(c.getSource(), "jarak generator: " + HorizonConfig.radius + " blok");
											return 1;
										})))
						.then(ClientCommandManager.literal("tes")
								.then(ClientCommandManager.literal("on").executes(c -> {
									say(c.getSource(), HorizonTest.start());
									return 1;
								}))
								.then(ClientCommandManager.literal("off").executes(c -> {
									HorizonTest.stop();
									say(c.getSource(), "bukit tes dihapus");
									return 1;
								})))
						.then(ClientCommandManager.literal("generator")
								.then(ClientCommandManager.literal("on").executes(c -> {
									HorizonConfig.generator = true;
									HorizonConfig.save();
									HorizonWorker.settingsChanged();
									say(c.getSource(), "generator dunia: ON");
									return 1;
								}))
								.then(ClientCommandManager.literal("off").executes(c -> {
									HorizonConfig.generator = false;
									HorizonConfig.save();
									say(c.getSource(), "generator dunia: OFF (peta cuma dari chunk yang kamu datangi)");
									return 1;
								})))));
	}
}
