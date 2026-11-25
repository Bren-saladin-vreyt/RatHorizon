package com.svreg.horizon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Option 3, stage 1 (/horizon tes on): one made-up hill drawn by the mod as real 3D geometry, far outside the render
 * distance, to find out on the phone whether geometry drawn by a mod shows there at all (Iris + MobileGlues), how the
 * shader treats it and what it costs. Nothing of this stays: it is only the test.
 */
public final class HorizonTest {
	private static volatile boolean on = false;
	private static double hx, hy, hz;          // the hill's foot, in the world
	private static float[] verts = new float[0]; // x, y, z (relative to the foot) and colour, 4 corners per quad
	private static final int G = 48;             // cells across
	private static final float R = 260f, H = 150f;

	private HorizonTest() {
	}

	public static void register() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(HorizonTest::render);
	}

	/** puts the hill in front of the player, at 2.2 x the render distance */
	public static String start() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return "masuk ke dunia dulu";
		}
		int rd = mc.options.getEffectiveRenderDistance() * 16;
		double dist = rd * 2.2 + R * 0.5;
		Vec3 look = mc.player.getLookAngle();
		double lx = look.x, lz = look.z, l = Math.sqrt(lx * lx + lz * lz);
		if (l < 1e-3) {
			lx = 1;
			lz = 0;
			l = 1;
		}
		hx = mc.player.getX() + lx / l * dist;
		hz = mc.player.getZ() + lz / l * dist;
		hy = Math.floor(mc.player.getY()) - 12.0;
		build();
		on = true;
		return "bukit tes dipasang " + Math.round(dist) + " blok di depan kamu (render distance kamu " + rd + " blok). Lihat ke arah depan sekarang";
	}

	public static void stop() {
		on = false;
	}

	private static float height(float x, float z) {
		float d = (float) Math.sqrt(x * x + z * z) / R;
		if (d >= 1f) {
			return 0f;
		}
		float s = 1f - d;
		float h = H * s * s * (3f - 2f * s);
		h += 9f * (float) (Math.sin(x * 0.045) * Math.cos(z * 0.05)) * s; // a few bumps
		return Math.max(h, 0f);
	}

	private static int colour(float h, float nx, float ny, float nz) {
		float r, g, b;
		if (h > H * 0.82f) {            // snow
			r = 0.93f; g = 0.95f; b = 0.98f;
		} else if (ny < 0.72f) {        // steep: rock
			r = 0.55f; g = 0.55f; b = 0.58f;
		} else if (h < 2f) {            // the foot: a strip of sand
			r = 0.86f; g = 0.80f; b = 0.58f;
		} else {                        // grass, a little darker higher up
			float k = h / H;
			r = 0.42f - 0.10f * k; g = 0.74f - 0.12f * k; b = 0.32f;
		}
		// flat cartoon light from one side, baked in (the shader's basic program draws colours as they are)
		float lit = Math.max(0f, nx * 0.55f + ny * 0.75f + nz * 0.35f);
		float shade = lit > 0.62f ? 1.0f : lit > 0.35f ? 0.86f : 0.72f;
		int ir = (int) (Math.min(1f, r * shade) * 255), ig = (int) (Math.min(1f, g * shade) * 255), ib = (int) (Math.min(1f, b * shade) * 255);
		return 0xFF000000 | ir << 16 | ig << 8 | ib;
	}

	private static void build() {
		float cell = 2f * R / G;
		float[] v = new float[G * G * 4 * 4];
		int n = 0;
		for (int j = 0; j < G; j++) {
			for (int i = 0; i < G; i++) {
				float x0 = -R + i * cell, z0 = -R + j * cell, x1 = x0 + cell, z1 = z0 + cell;
				float h00 = height(x0, z0), h10 = height(x1, z0), h01 = height(x0, z1), h11 = height(x1, z1);
				if (h00 + h10 + h01 + h11 <= 0f) {
					continue;
				}
				// the cell's normal from its corners
				float nx = (h00 + h01 - h10 - h11) * 0.5f, nz = (h00 + h10 - h01 - h11) * 0.5f, ny = cell;
				float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				nx /= nl; ny /= nl; nz /= nl;
				float c = Float.intBitsToFloat(colour((h00 + h10 + h01 + h11) * 0.25f, nx, ny, nz));
				float[] q = {x0, h00, z0, c, x0, h01, z1, c, x1, h11, z1, c, x1, h10, z0, c};
				System.arraycopy(q, 0, v, n, 16);
				n += 16;
			}
		}
		float[] out = new float[n];
		System.arraycopy(v, 0, out, 0, n);
		verts = out;
	}

	private static void render(WorldRenderContext context) {
		if (!on) {
			return;
		}
		try {
			Vec3 cam = context.worldState().cameraRenderState.pos;
			PoseStack ps = context.matrices();
			ps.pushPose();
			ps.translate(hx - cam.x, hy - cam.y, hz - cam.z);
			Matrix4f m = ps.last().pose();
			VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugFilledBox());
			float[] v = verts;
			for (int k = 0; k < v.length; k += 4) {
				vc.addVertex(m, v[k], v[k + 1], v[k + 2]).setColor(Float.floatToRawIntBits(v[k + 3]));
			}
			ps.popPose();
		} catch (Throwable t) {
			on = false;
			SvregHorizon.LOG.error("SvregHorizon: test hill failed", t);
			HorizonMap.chat("bukit tes gagal digambar: " + t);
		}
	}
}
