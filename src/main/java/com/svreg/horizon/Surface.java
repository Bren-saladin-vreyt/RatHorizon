package com.svreg.horizon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * sorts the top block of a column into the few kinds of ground the far land paints (the shader has the same list):
 * by the block's map colour, the one the game's own maps use. 0 = not known (the shader goes by the biome).
 * Stored in the high 4 bits of the sample's biome byte (the biome kind in the low 4).
 */
public final class Surface {
	public static final int NONE = 0, GRASS = 1, FOLIAGE = 2, SAND = 3, SNOW = 4, STONE = 5, DIRT = 6, WOOD = 7,
			TERRACOTTA = 8, LIGHT = 9, DARK = 10, CROPS = 11, BRICK = 12, PINK = 13, LAVA = 14, BLUE = 15;

	private Surface() {
	}

	public static int kind(BlockState state, BlockGetter level, BlockPos pos) {
		MapColor c;
		try {
			c = state.getMapColor(level, pos);
		} catch (Throwable t) {
			return NONE;
		}
		if (c == null) return NONE;
		int id = c.id;
		switch (id) {
			case 1: case 19: return GRASS;                 // grass, light green
			case 7: case 27: case 33: case 61: return FOLIAGE; // plants and leaves, green, emerald, glow lichen
			case 2: case 30: return SAND;                  // sand, gold
			case 5: case 8: return SNOW;                   // ice, snow
			case 6: case 11: case 22: case 60: return STONE; // metal, stone, light grey, raw iron
			case 10: case 26: case 34: return DIRT;        // dirt (paths, farmland), brown, podzol
			case 13: return WOOD;                          // wood (planks, logs, village roofs)
			case 4: return LAVA;                           // fire, lava
			case 3: case 9: case 14: return LIGHT;         // wool, clay, quartz
			case 21: case 29: case 59: return DARK;        // grey, black, deepslate
			case 18: return CROPS;                         // yellow (hay)
			case 16: case 20: case 24: return PINK;        // magenta, pink (cherry leaves), purple
			case 17: case 23: case 25: case 31: case 32: return BLUE;
			case 15: case 28: case 35: return TERRACOTTA;  // orange, red, nether
			default:
				if (id >= 36 && id <= 58) return TERRACOTTA; // terracotta colours, crimson and warped
				return NONE;
		}
	}
}
