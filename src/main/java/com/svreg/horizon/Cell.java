package com.svreg.horizon;

/**
 * One map sample (8 x 8 blocks) packed in an int. 0 = not known yet.
 * bits 0-15 height + 128 (block y of the first air above the surface, water counts as surface)
 * bits 16-23 kind of biome (see Biomes) in the low 4, kind of ground seen from above (see Surface) in the high 4, bit 24 water on top, bit 25 from a real chunk (wins over the generator),
 * bit 26 known.
 */
public final class Cell {
	public static final int WATER = 1 << 24, CHUNK = 1 << 25, KNOWN = 1 << 26;

	private Cell() {
	}

	public static int pack(int height, int biome, boolean water, boolean chunk) {
		int h = Math.max(0, Math.min(65535, height + 128));
		return h | ((biome & 255) << 16) | (water ? WATER : 0) | (chunk ? CHUNK : 0) | KNOWN;
	}

	/**
	 * The texture's pixel for a sample at map texel (tx, tz), in NativeImage's own byte order (ABGR in an int):
	 * R, G = height + 128 (high, low byte), B = kind of biome + 16 x kind of ground,
	 * A = 128 known | 64 water | 16 (fixed mark: bits 4-5 = 01) | (tz / 512 mod 4) << 2 | (tx / 512 mod 4):
	 * the shader checks the mark and which 512-texel period the texel holds, so an old texel from the other side of
	 * the wrapped map (or a missing texture) is never read as land.
	 */
	public static int pixelABGR(int cell, int tx, int tz) {
		if ((cell & KNOWN) == 0) {
			return 0;
		}
		int h = cell & 0xFFFF;
		int r = h >> 8, g = h & 255, b = (cell >> 16) & 255;
		int a = 128 | ((cell & WATER) != 0 ? 64 : 0) | 16 | ((Math.floorDiv(tz, 512) & 3) << 2) | (Math.floorDiv(tx, 512) & 3);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}
