package com.svreg.horizon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SvregHorizon: "Distant Horizons versi kita" for the SvregSky Shader Remade pack.
 * Keeps a map of the real world's surface (height, water, kind of biome) around the player, one sample per 8 x 8
 * blocks, 512 x 512 samples (4096 x 4096 blocks), and hands it to the shader as the dynamic texture
 * svreghorizon:dynamic/heightmap (the pack reads it with customTexture.flMap). No mixin into Iris.
 * Where the data comes from:
 *  - chunks the client loads (anywhere, also on servers): the real surface, trees and buildings included
 *  - in single player, the world generator, asked directly for the ground height (no chunks made), for every place
 *    not seen yet, worked through from near to far on a background thread
 * Everything is kept on disk per world and dimension (.minecraft/svreghorizon/), so it is there next time.
 */
public class SvregHorizon implements ClientModInitializer {
	public static final String MOD_ID = "svreghorizon";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOG.info("SvregHorizon loaded");
		HorizonConfig.load();
		HorizonCommands.register();
		HorizonTest.register();
		HorizonScreen.register();
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> HorizonMap.init());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			HorizonMap.tick(client);
			HorizonScreen.tick(client);
			HorizonTest.tick();
		});
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> ChunkSampler.onLoad(level, chunk));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HorizonMap.leave());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> HorizonMap.leave());
	}
}
