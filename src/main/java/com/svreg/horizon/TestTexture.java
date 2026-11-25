package com.svreg.horizon;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL33C;

/**
 * The test picture, 512 x 512, the same in both textures:
 *  - a rainbow gradient that slides 32 texels sideways every second (so you can see it is updated live)
 *  - in the corner texels (0..7, 0..7) a 1-texel black/white checker: the shader samples exactly between four of
 *    them; LINEAR filtering gives grey (0.5), NEAREST gives pure black or white. Stage 2 needs LINEAR.
 *  - texel (8, 0) holds the seconds counter (red = seconds mod 256), for debugging.
 */
public final class TestTexture {
	public static final int N = 512;
	/** like the vanilla lightmap's id (minecraft:dynamic/lightmap_1) */
	public static final Identifier ID_A = Identifier.fromNamespaceAndPath(SvregHorizon.MOD_ID, "dynamic/heightmap");
	/** like a texture file in a resource pack */
	public static final Identifier ID_B = Identifier.fromNamespaceAndPath(SvregHorizon.MOD_ID, "textures/heightmap.png");

	private static DynamicTexture texA, texB;
	private static long lastSecond = -1;
	private static boolean broken = false;

	private TestTexture() {
	}

	public static void init() {
		if (texA != null || broken) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			texA = new DynamicTexture(() -> "SvregHorizon heightmap A", new NativeImage(N, N, false));
			texB = new DynamicTexture(() -> "SvregHorizon heightmap B", new NativeImage(N, N, false));
			paint(0);
			mc.getTextureManager().register(ID_A, texA);
			mc.getTextureManager().register(ID_B, texB);
			LOG("registered " + ID_A + " and " + ID_B + " (" + N + "x" + N + ", linear)");
		} catch (Throwable t) {
			broken = true;
			SvregHorizon.LOG.error("SvregHorizon: could not make the test texture", t);
		}
	}

	public static void tick() {
		if (texA == null || broken) {
			return;
		}
		// v0.1.1: the first test showed NEAREST: the texture's own LINEAR setting loses to a sampler object left bound
		// on the unit Iris puts our texture on. Every tick: our texture's LINEAR again, and no sampler object on the
		// higher units (3..31; Minecraft's own draws use the low ones and bind their samplers themselves), so the
		// texture's own setting counts when Iris samples it
		try {
			linear(texA);
			linear(texB);
			int units = Math.min(32, GL11C.glGetInteger(0x8872 /* GL_MAX_TEXTURE_IMAGE_UNITS */));
			for (int u = 3; u < units; u++) {
				GL33C.glBindSampler(u, 0);
			}
		} catch (Throwable t) {
			broken = true;
			SvregHorizon.LOG.error("SvregHorizon: filter fix failed", t);
			return;
		}
		long s = System.currentTimeMillis() / 1000L;
		if (s == lastSecond) {
			return;
		}
		lastSecond = s;
		try {
			paint(s);
		} catch (Throwable t) {
			broken = true;
			SvregHorizon.LOG.error("SvregHorizon: test texture update failed", t);
		}
	}

	private static void paint(long sec) {
		int shift = (int) ((sec * 32L) % N);
		fill(texA.getPixels(), shift, sec);
		fill(texB.getPixels(), shift, sec);
		texA.upload();
		texB.upload();
		linear(texA);
		linear(texB);
	}

	private static void fill(NativeImage img, int shift, long sec) {
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				float u = ((x + shift) % N) / (float) N;
				float v = y / (float) N;
				int r = (int) (255 * (0.5 + 0.5 * Math.sin(u * 6.2831853)));
				int g = (int) (255 * (0.5 + 0.5 * Math.sin(u * 6.2831853 + 2.094)));
				int b = (int) (255 * v);
				img.setPixel(x, y, argb(255, r, g, b));
			}
		}
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				int c = ((x + y) & 1) == 0 ? 255 : 0;
				img.setPixel(x, y, argb(255, c, c, c));
			}
		}
		img.setPixel(8, 0, argb(255, (int) (sec & 255), 0, 0));
	}

	private static int argb(int a, int r, int g, int b) {
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** linear filtering, clamped edges, no mipmaps: set on the GL texture itself, for whoever binds it (Iris) */
	private static void linear(DynamicTexture tex) {
		if (!(tex.getTexture() instanceof GlTexture gt)) {
			return;
		}
		GlStateManager._bindTexture(gt.glId());
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, 33071); // clamp to edge
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, 33071);
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, 33085, 0); // max level 0
		GlStateManager._bindTexture(0);
	}

	private static void LOG(String s) {
		SvregHorizon.LOG.info("SvregHorizon: " + s);
	}
}
