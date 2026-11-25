package com.svreg.horizon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** a chunk the client loaded: its surface, one sample per 8 x 8 blocks (4 per chunk), queued for the worker */
public final class ChunkSampler {
	private ChunkSampler() {
	}

	public static void onLoad(ClientLevel level, LevelChunk chunk) {
		try {
			int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int j = 0; j < 2; j++) {
				for (int i = 0; i < 2; i++) {
					int lx = i * 8 + 4, lz = j * 8 + 4;
					int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz); // y of the top block
					if (top <= level.getMinY()) {
						continue; // nothing there (void, or not filled yet)
					}
					p.set(x0 + lx, top, z0 + lz);
					boolean water = chunk.getBlockState(p).getFluidState().is(FluidTags.WATER);
					int kind = Biomes.kind(chunk.getNoiseBiome(QuartPos.fromBlock(x0 + lx), QuartPos.fromBlock(top), QuartPos.fromBlock(z0 + lz)));
					HorizonWorker.offer(Math.floorDiv(x0 + lx, 8), Math.floorDiv(z0 + lz, 8), Cell.pack(top + 1, kind, water, true));
				}
			}
		} catch (Throwable t) {
			SvregHorizon.LOG.debug("SvregHorizon: chunk sample failed", t);
		}
	}
}
