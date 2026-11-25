package com.svreg.horizon;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** sorts biomes into the few kinds the painted far land colours (the shader has the same list) */
public final class Biomes {
	public static final int PLAINS = 0, FOREST = 1, JUNGLE = 2, TAIGA = 3, SAND = 4, BADLANDS = 5, SNOW = 6, SAVANNA = 7,
			SWAMP = 8, OCEAN = 9, RIVER = 10, STONE = 11, CHERRY = 12, MUSHROOM = 13, MEADOW = 14;

	private Biomes() {
	}

	public static int kind(Holder<Biome> biome) {
		String n;
		try {
			n = biome.getRegisteredName();
		} catch (Throwable t) {
			return PLAINS;
		}
		if (n == null) return PLAINS;
		n = n.substring(n.indexOf(':') + 1);
		if (n.contains("ocean")) return OCEAN;
		if (n.contains("river")) return RIVER;
		if (n.contains("snowy") || n.contains("frozen") || n.contains("ice") || n.contains("grove") || n.contains("jagged")) return SNOW;
		if (n.contains("beach") || n.contains("desert")) return SAND;
		if (n.contains("badlands")) return BADLANDS;
		if (n.contains("cherry")) return CHERRY;
		if (n.contains("mushroom")) return MUSHROOM;
		if (n.contains("meadow")) return MEADOW;
		if (n.contains("stony") || n.contains("windswept") || n.contains("peaks")) return STONE;
		if (n.contains("swamp") || n.contains("mangrove")) return SWAMP;
		if (n.contains("savanna")) return SAVANNA;
		if (n.contains("jungle") || n.contains("dark_forest") || n.contains("bamboo") || n.contains("pale_garden")) return JUNGLE;
		if (n.contains("taiga")) return TAIGA;
		if (n.contains("forest") || n.contains("birch")) return FOREST;
		return PLAINS;
	}
}
