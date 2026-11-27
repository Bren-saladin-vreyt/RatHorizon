package com.svreg.horizon;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The SvregHorizon menu: opened with its key (H, can be changed in Controls) or by typing /horizon in chat.
 * Shows what the map is doing and holds the mod's settings (saved at once). How far the land is drawn and its haze
 * are the shader's own settings (menu Daratan Jauh in the shaderpack).
 */
public class HorizonScreen extends Screen {
	private static final int[] RADII = {256, 512, 1000, 1400, 2048};
	private static KeyMapping key;
	private static boolean openNextTick = false;

	private final Screen parent;

	public HorizonScreen(Screen parent) {
		super(Component.literal("SvregHorizon - Daratan Jauh"));
		this.parent = parent;
	}

	// ---------------------------------------------------------------- opening it
	public static void register() {
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.svreghorizon.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KeyMapping.Category.MISC));
	}

	/** from a chat command: the chat screen closes after the command, so the menu opens on the next tick */
	public static void openSoon() {
		openNextTick = true;
	}

	public static void tick(Minecraft mc) {
		if (openNextTick && mc.screen == null) {
			openNextTick = false;
			mc.setScreen(new HorizonScreen(null));
		}
		if (key != null) {
			while (key.consumeClick()) {
				if (mc.screen == null) {
					mc.setScreen(new HorizonScreen(null));
				}
			}
		}
	}

	// ---------------------------------------------------------------- the menu
	private static Component onOff(String name, boolean v) {
		return Component.empty()
				.append(Component.literal(v ? "ON  " : "OFF  ").withStyle(v ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD))
				.append(Component.literal(name));
	}

	private static Component radiusText() {
		return Component.empty()
				.append(Component.literal(HorizonConfig.radius + " blok  ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
				.append(Component.literal("Jarak generator"));
	}

	@Override
	protected void init() {
		int w = 220, x = this.width / 2 - w / 2, y = Math.max(70, this.height / 2 - 40);
		this.addRenderableWidget(Button.builder(onOff("Generator dunia", HorizonConfig.generator), b -> {
					HorizonConfig.generator = !HorizonConfig.generator;
					HorizonConfig.save();
					HorizonWorker.settingsChanged();
					b.setMessage(onOff("Generator dunia", HorizonConfig.generator));
				})
				.pos(x, y).size(w, 20)
				.tooltip(Tooltip.create(Component.literal("Singleplayer: ngisi peta dari generator dunia, jauh di luar chunk yang pernah kamu datangi. OFF = peta cuma dari chunk yang kamu lihat.")))
				.build());
		this.addRenderableWidget(Button.builder(radiusText(), b -> {
					int next = RADII[0];
					for (int r : RADII) {
						if (r > HorizonConfig.radius) {
							next = r;
							break;
						}
					}
					HorizonConfig.radius = HorizonConfig.clamp(next);
					HorizonConfig.save();
					HorizonWorker.settingsChanged();
					b.setMessage(radiusText());
				})
				.pos(x, y + 24).size(w, 20)
				.tooltip(Tooltip.create(Component.literal("Seberapa jauh dari kamu peta diisi (ketuk buat ganti). Kecil = cepat penuh dan enteng. Samain sama Jarak Daratan Jauh di menu shader.")))
				.build());
		this.addRenderableWidget(Button.builder(Component.literal("Selesai"), b -> this.onClose())
				.pos(this.width / 2 - 60, this.height - 28).size(120, 20)
				.build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFF7FDBFF);
		String[] st = HorizonWorker.status();
		int y = 26;
		for (String line : st) {
			g.drawCenteredString(this.font, line, this.width / 2, y, 0xFFE0E0E0);
			y += 11;
		}
		g.drawCenteredString(this.font, "Jarak gambar & kabut daratan: menu shader > Daratan Jauh", this.width / 2, this.height - 42, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
