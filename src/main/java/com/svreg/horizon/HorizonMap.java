package com.svreg.horizon;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.lwjgl.system.MemoryUtil;

/**
 * Render-thread side: the 512 x 512 texture the shader reads (svreghorizon:dynamic/heightmap), which world and
 * dimension we are in, where the player is. The worker thread does the rest and hands over finished pictures.
 * The map is pinned to the world and wraps around: map texel (tx, tz) (= blocks 8tx..8tx+7) sits in pixel
 * (tx mod 512, tz mod 512), so walking never shifts the picture, only the edge ahead is refilled.
 */
public final class HorizonMap {
	public static final int N = 512;
	public static final Identifier ID = Identifier.fromNamespaceAndPath(SvregHorizon.MOD_ID, "dynamic/heightmap");

	private static DynamicTexture tex;
	private static String sessionKey = null;
	private static boolean broken = false;

	private HorizonMap() {
	}

	public static void init() {
		if (tex != null || broken) {
			return;
		}
		try {
			tex = new DynamicTexture(() -> "SvregHorizon map", new NativeImage(N, N, true));
			tex.upload();
			Minecraft.getInstance().getTextureManager().register(ID, tex);
			HorizonWorker.startThread();
			SvregHorizon.LOG.info("SvregHorizon: map texture {} ready ({}x{})", ID, N, N);
		} catch (Throwable t) {
			broken = true;
			SvregHorizon.LOG.error("SvregHorizon: could not make the map texture", t);
		}
	}

	public static void tick(Minecraft mc) {
		if (tex == null || broken) {
			return;
		}
		try {
			if (mc.level == null || mc.player == null) {
				if (sessionKey != null) {
					leave();
				}
			} else {
				String world;
				ServerLevel sl = null;
				IntegratedServer server = mc.getSingleplayerServer();
				if (server != null) {
					world = "sp_" + server.getWorldData().getLevelName();
					sl = server.getLevel(mc.level.dimension());
				} else {
					world = "mp_" + (mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "unknown");
				}
				String dim = mc.level.dimension().toString();
				String key = world + "|" + dim;
				if (!key.equals(sessionKey)) {
					sessionKey = key;
					HorizonWorker.begin(new HorizonWorker.Session(safe(world), safe(dim), sl, mc.level.getMinY()));
					clear();
				}
				HorizonWorker.setCenter(Math.floorDiv((int) Math.floor(mc.player.getX()), 8), Math.floorDiv((int) Math.floor(mc.player.getZ()), 8));
			}
			int[] pic = HorizonWorker.takePicture();
			if (pic != null) {
				show(pic);
			}
		} catch (Throwable t) {
			broken = true;
			SvregHorizon.LOG.error("SvregHorizon: map update failed", t);
		}
	}

	public static void leave() {
		if (sessionKey != null) {
			sessionKey = null;
			HorizonWorker.begin(null);
		}
		if (tex != null && !broken) {
			try {
				clear();
			} catch (Throwable ignored) {
			}
		}
	}

	private static void clear() {
		show(new int[N * N]);
	}

	/** pixels in NativeImage's byte order (ABGR ints) into the texture */
	private static void show(int[] abgr) {
		NativeImage img = tex.getPixels();
		if (img == null) {
			return;
		}
		boolean done = false;
		try {
			long ptr = img.getPointer();
			if (ptr != 0L) {
				MemoryUtil.memIntBuffer(ptr, N * N).put(abgr);
				done = true;
			}
		} catch (Throwable ignored) {
		}
		if (!done) {
			for (int y = 0; y < N; y++) {
				for (int x = 0; x < N; x++) {
					int c = abgr[x + y * N];
					// setPixel takes ARGB: swap red and blue
					img.setPixel(x, y, (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF));
				}
			}
		}
		tex.upload();
	}

	private static String safe(String s) {
		return s.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}
