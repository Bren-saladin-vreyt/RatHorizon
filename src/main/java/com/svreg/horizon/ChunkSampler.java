package com.svreg.horizon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** a chunk the client loaded: its surface, one sample per 8 x 8 blocks (4 per chunk, the ground without trees, and what covers it seen from above), queued for the worker */
public final class ChunkSampler {
	private ChunkSampler() {
	}

	public static void onLoad(ClientLevel level, LevelChunk chunk) {
		try {
			int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			int[] hs = new int[64];
			for (int j = 0; j < 2; j++) {
				for (int i = 0; i < 2; i++) {
					// the ground of this 8 x 8: leaves left out (MOTION_BLOCKING_NO_LEAVES), and of its 64 columns the
					// lower quarter (sorted, the 16th), so a tree trunk or a lone pole does not make the whole patch a
					// tower -- the same kind of ground the world generator gives (no trees)
					int n = 0;
					for (int lz = j * 8; lz < j * 8 + 8; lz++) {
						for (int lx = i * 8; lx < i * 8 + 8; lx++) {
							int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz); // y of the top block
							if (top > level.getMinY()) {
								hs[n++] = top;
							}
						}
					}
					if (n < 32) {
						continue; // mostly nothing there (void, or not filled yet)
					}
					java.util.Arrays.sort(hs, 0, n);
					int top = hs[n / 4];
					int cx = x0 + i * 8 + 4, cz = z0 + j * 8 + 4;
					// water: the middle column's top is water and it is not above the ground picked
					int mid = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, i * 8 + 4, j * 8 + 4);
					p.set(cx, mid, cz);
					boolean water = mid <= top + 1 && chunk.getBlockState(p).getFluidState().is(FluidTags.WATER);
					int kind = Biomes.kind(chunk.getNoiseBiome(QuartPos.fromBlock(cx), QuartPos.fromBlock(top), QuartPos.fromBlock(cz)));
					// what the patch looks like from above: the top block (leaves and roofs included) of every other
					// column, the kind most of them are
					int[] votes = new int[16];
					for (int lz = j * 8; lz < j * 8 + 8; lz += 2) {
						for (int lx = i * 8; lx < i * 8 + 8; lx += 2) {
							int ty = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
							p.set(x0 + lx, ty, z0 + lz);
							votes[Surface.kind(chunk.getBlockState(p), chunk, p)]++;
						}
					}
					int surf = 0;
					for (int k = 1; k < 16; k++) {
						if (votes[k] > votes[surf] || (surf == 0 && votes[k] > 0 && votes[k] >= votes[0])) {
							surf = k;
						}
					}
					HorizonWorker.offer(Math.floorDiv(cx, 8), Math.floorDiv(cz, 8), Cell.pack(top + 1, kind | (surf << 4), water, true));
				}
			}
		} catch (Throwable t) {
			SvregHorizon.LOG.debug("SvregHorizon: chunk sample failed", t);
		}
	}
}
