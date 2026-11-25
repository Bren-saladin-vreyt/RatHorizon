package com.svreg.horizon;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** the player's settings (/horizon), kept in .minecraft/config/svreghorizon.properties */
public final class HorizonConfig {
	/** how far from the player the world generator fills the map, in blocks (the map itself reaches 2048) */
	public static volatile int radius = 2048;
	/** the world generator on or off (single player); chunks are always sampled */
	public static volatile boolean generator = true;

	public static final int MIN_RADIUS = 256, MAX_RADIUS = 2048;

	private HorizonConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("svreghorizon.properties");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(f)) {
			p.load(in);
			radius = clamp(Integer.parseInt(p.getProperty("radius", "2048").trim()));
			generator = Boolean.parseBoolean(p.getProperty("generator", "true").trim());
		} catch (Exception e) {
			SvregHorizon.LOG.warn("SvregHorizon: could not read {}", f);
		}
	}

	public static void save() {
		Properties p = new Properties();
		p.setProperty("radius", Integer.toString(radius));
		p.setProperty("generator", Boolean.toString(generator));
		try {
			Files.createDirectories(file().getParent());
			try (OutputStream out = Files.newOutputStream(file())) {
				p.store(out, "SvregHorizon (ubah lewat /horizon di chat)");
			}
		} catch (Exception e) {
			SvregHorizon.LOG.warn("SvregHorizon: could not write {}", file());
		}
	}

	public static int clamp(int r) {
		return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r));
	}
}
