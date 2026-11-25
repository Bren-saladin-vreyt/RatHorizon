package com.svreg.horizon;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The background thread: owns the map data (tiles of 64 x 64 samples, kept on disk per world and dimension),
 * takes in the chunk samples, asks the world generator (single player) for every sample not known yet, from the
 * player outward, and builds the texture's picture about twice a second when something changed.
 * It works in short slices with pauses, so the game never waits for it.
 */
public final class HorizonWorker {
	public record Session(String world, String dim, ServerLevel level, int minY) {
	}

	private static final int N = HorizonMap.N, HALF = N / 2, TILE = 64;
	private static final long SLICE_NS = 5_000_000L;      // work at most 5 ms ...
	private static final long PAUSE_MS = 8L;              // ... then rest 8 ms (the generator uses about a third of a core)

	private static volatile Session pending = null;
	private static volatile boolean newSession = false;
	private static volatile int centerX = 0, centerZ = 0;
	private static final ConcurrentLinkedQueue<long[]> offers = new ConcurrentLinkedQueue<>();
	private static final AtomicReference<int[]> picture = new AtomicReference<>();
	private static Thread thread;

	// owned by the worker thread
	private static final class Tile {
		final int[] v = new int[TILE * TILE];
		boolean dirty;
		long used;
	}

	private static Session session;
	private static final Map<Long, Tile> tiles = new HashMap<>();
	private static Path dir;
	private static int changes = 0, builtX = Integer.MIN_VALUE, builtZ = Integer.MIN_VALUE;
	private static long lastBuild = 0, lastSave = 0;
	// the generator's walk outward: ring r around (scanX, scanZ), step k along it
	private static int scanX, scanZ, ring, step;
	private static boolean scanDone;
	private static int reported = 0;
	private static boolean genError = false;

	private HorizonWorker() {
	}

	// ---------------------------------------------------------------- called from the render thread
	public static synchronized void startThread() {
		if (thread != null) {
			return;
		}
		thread = new Thread(HorizonWorker::run, "SvregHorizon worker");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	public static void begin(Session s) {
		pending = s;
		newSession = true;
	}

	public static void setCenter(int tx, int tz) {
		centerX = tx;
		centerZ = tz;
	}

	public static void offer(int tx, int tz, int cell) {
		offers.add(new long[]{tx, tz, cell});
	}

	public static int[] takePicture() {
		return picture.getAndSet(null);
	}

	// ---------------------------------------------------------------- the worker thread
	private static void run() {
		while (true) {
			try {
				loop();
				Thread.sleep(PAUSE_MS);
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				SvregHorizon.LOG.error("SvregHorizon: worker error", t);
				try {
					Thread.sleep(1000L);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private static void loop() {
		if (newSession) {
			newSession = false;
			saveAll();
			tiles.clear();
			session = pending;
			reported = 0;
			genError = false;
			dir = session == null ? null : FabricLoader.getInstance().getGameDir().resolve("svreghorizon").resolve(session.world()).resolve(session.dim());
			changes = 1;
			builtX = builtZ = Integer.MIN_VALUE;
			restartScan(centerX, centerZ);
			if (session != null) {
				SvregHorizon.LOG.info("SvregHorizon: map for {} {} ({})", session.world(), session.dim(), session.level() != null ? "chunks + world generator" : "chunks only");
				HorizonMap.chat("peta aktif (" + (session.level() != null ? "chunk + generator dunia" : "chunk saja, generator cuma jalan di singleplayer") + "), posisi peta " + centerX * 8 + ", " + centerZ * 8);
			}
		}
		if (session == null) {
			// (chunk samples that came in before the session is known wait for it; a long stay in a menu caps them)
			while (offers.size() > 20000) {
				offers.poll();
			}
			return;
		}
		long t0 = System.nanoTime();
		long[] o;
		while ((o = offers.poll()) != null) {
			set((int) o[0], (int) o[1], (int) o[2], true);
		}
		int cx = centerX, cz = centerZ;
		// the generator: walk outward from the player, filling what is still unknown
		if (session.level() != null) {
			if (Math.abs(cx - scanX) > 16 || Math.abs(cz - scanZ) > 16) {
				restartScan(cx, cz); // moved on: start again from the player (known samples are skipped quickly)
			}
			ChunkGenerator gen = session.level().getChunkSource().getGenerator();
			RandomState rs = session.level().getChunkSource().randomState();
			int sea = gen.getSeaLevel();
			while (!scanDone && System.nanoTime() - t0 < SLICE_NS) {
				int tx, tz;
				if (ring == 0) {
					tx = scanX;
					tz = scanZ;
				} else {
					// ring r: 8r samples, walked side by side
					int side = step / (2 * ring), o2 = step % (2 * ring);
					switch (side) {
						case 0 -> { tx = scanX - ring + o2; tz = scanZ - ring; }
						case 1 -> { tx = scanX + ring; tz = scanZ - ring + o2; }
						case 2 -> { tx = scanX + ring - o2; tz = scanZ + ring; }
						default -> { tx = scanX - ring; tz = scanZ + ring - o2; }
					}
				}
				if (++step >= Math.max(1, 8 * ring)) {
					step = 0;
					if (++ring >= HALF) {
						scanDone = true;
					}
				}
				if (get(tx, tz) == 0) {
					try {
						set(tx, tz, generate(gen, rs, sea, tx, tz), false);
					} catch (Throwable t) {
						if (!genError) {
							genError = true;
							SvregHorizon.LOG.error("SvregHorizon: world generator failed", t);
							HorizonMap.chat("generator dunia gagal: " + t);
						}
						scanDone = true;
						break;
					}
				}
				// progress, for testing: 500, 1000, 2000 blocks around you done
				int doneBlocks = scanDone ? HALF * 8 : (ring - 1) * 8;
				if (reported < 3 && doneBlocks >= (500 << reported)) {
					HorizonMap.chat("generator: sampai " + (500 << reported) + " blok di sekitar kamu udah keisi");
					reported++;
				}
			}
		}
		long now = System.currentTimeMillis();
		if (now - lastBuild > 500L && (changes > 0 || cx != builtX || cz != builtZ)) {
			build(cx, cz);
			lastBuild = now;
		}
		if (now - lastSave > 60_000L) {
			lastSave = now;
			saveAll();
			evict(cx, cz);
		}
	}

	private static void restartScan(int cx, int cz) {
		scanX = cx;
		scanZ = cz;
		ring = 0;
		step = 0;
		scanDone = false;
	}

	/** the ground at one sample, from the world generator (no chunk is made) */
	private static int generate(ChunkGenerator gen, RandomState rs, int sea, int tx, int tz) {
		int bx = tx * 8 + 4, bz = tz * 8 + 4;
		ServerLevel lv = session.level();
		int h = gen.getBaseHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, lv, rs);
		boolean water = false;
		if (h <= sea) {
			water = gen.getBaseHeight(bx, bz, Heightmap.Types.OCEAN_FLOOR_WG, lv, rs) < h;
		}
		int kind = Biomes.kind(gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(Math.max(h - 1, session.minY())), QuartPos.fromBlock(bz), rs.sampler()));
		return Cell.pack(h, kind, water, false);
	}

	/** the texture's picture for the 512 x 512 samples around the player */
	private static void build(int cx, int cz) {
		int[] pic = new int[N * N];
		for (int sy = 0; sy < N; sy++) {
			int tz = cz + Math.floorMod(sy - cz + HALF, N) - HALF;
			for (int sx = 0; sx < N; sx++) {
				int tx = cx + Math.floorMod(sx - cx + HALF, N) - HALF;
				pic[sx + sy * N] = Cell.pixelABGR(get(tx, tz), tx, tz);
			}
		}
		picture.set(pic);
		changes = 0;
		builtX = cx;
		builtZ = cz;
	}

	// ---------------------------------------------------------------- tiles
	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static Tile tile(int tx, int tz) {
		int x = Math.floorDiv(tx, TILE), z = Math.floorDiv(tz, TILE);
		long k = key(x, z);
		Tile t = tiles.get(k);
		if (t == null) {
			t = new Tile();
			load(t, x, z);
			tiles.put(k, t);
		}
		t.used = System.currentTimeMillis();
		return t;
	}

	private static int get(int tx, int tz) {
		return tile(tx, tz).v[Math.floorMod(tx, TILE) + Math.floorMod(tz, TILE) * TILE];
	}

	private static void set(int tx, int tz, int cell, boolean fromChunk) {
		Tile t = tile(tx, tz);
		int i = Math.floorMod(tx, TILE) + Math.floorMod(tz, TILE) * TILE;
		int old = t.v[i];
		if (!fromChunk && (old & Cell.CHUNK) != 0) {
			return; // a real chunk's sample is never replaced by the generator's guess
		}
		if (old != cell) {
			t.v[i] = cell;
			t.dirty = true;
			changes++;
		}
	}

	private static Path file(int x, int z) {
		return dir.resolve("t_" + x + "_" + z + ".bin");
	}

	private static void load(Tile t, int x, int z) {
		if (dir == null) {
			return;
		}
		Path f = file(x, z);
		if (!Files.exists(f)) {
			return;
		}
		try (InputStream in = Files.newInputStream(f); DataInputStream d = new DataInputStream(new GZIPInputStream(in))) {
			for (int i = 0; i < t.v.length; i++) {
				t.v[i] = d.readInt();
			}
		} catch (IOException e) {
			SvregHorizon.LOG.warn("SvregHorizon: could not read {}", f);
		}
	}

	private static void saveAll() {
		if (dir == null) {
			return;
		}
		for (Map.Entry<Long, Tile> e : tiles.entrySet()) {
			Tile t = e.getValue();
			if (!t.dirty) {
				continue;
			}
			int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
			try {
				Files.createDirectories(dir);
				try (OutputStream out = Files.newOutputStream(file(x, z)); DataOutputStream d = new DataOutputStream(new GZIPOutputStream(out))) {
					for (int v : t.v) {
						d.writeInt(v);
					}
				}
				t.dirty = false;
			} catch (IOException ex) {
				SvregHorizon.LOG.warn("SvregHorizon: could not save a map tile", ex);
			}
		}
	}

	/** drops tiles far from the player that were not used for a while (they are on disk) */
	private static void evict(int cx, int cz) {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Tile> e = it.next();
			int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
			boolean far = Math.abs(x * TILE + TILE / 2 - cx) > N || Math.abs(z * TILE + TILE / 2 - cz) > N;
			if (far && !e.getValue().dirty && now - e.getValue().used > 30_000L) {
				it.remove();
			}
		}
	}
}
