package com.svreg.horizon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SvregHorizon: "Distant Horizons versi kita", step 1 (test mod).
 * Registers two dynamic 512x512 textures in Minecraft's TextureManager, under the two ids the shaderpack's
 * shaders.properties points at (customTexture.flTestA / flTestB, texture.composite.flTestC), and changes their
 * picture once a second. No mixin into Iris: Iris reads dynamic textures on its own.
 */
public class SvregHorizon implements ClientModInitializer {
	public static final String MOD_ID = "svreghorizon";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOG.info("SvregHorizon loaded (test texture)");
		// the TextureManager exists once the client has started (before any world, so before Iris builds its pipeline)
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> TestTexture.init());
		ClientTickEvents.END_CLIENT_TICK.register(client -> TestTexture.tick());
	}
}
