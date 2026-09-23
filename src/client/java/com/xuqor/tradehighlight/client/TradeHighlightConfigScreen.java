package com.xuqor.tradehighlight.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Config screen opened from Mod Menu: choose which enchanted books glow gold, prices, colors, sounds. */
public class TradeHighlightConfigScreen extends Screen {

	private static final int PER_PAGE = 10; // 2 columns x 5 rows

	private final Screen parent;
	private int page = 0;

	private EditBox maxPriceBox;
	private EditBox goldColorBox;
	private EditBox greenColorBox;
	private EditBox volumeBox;
	private EditBox protectedBlockBox;

	public TradeHighlightConfigScreen(Screen parent) {
		super(Component.literal("TradeHighlight"));
		this.parent = parent;
	}

	private static Component onOff(Component label, boolean on) {
		return label.copy().append(Component.literal(on ? ": ON" : ": OFF")
			.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	/** "" shown as "Vanilla: <id>" on a button; clicking cycles to the next file in the sounds folder. */
	private static Component soundLabel(String prefix, String currentFile, String vanillaId) {
		String shown = currentFile.isBlank() ? "vanilla (" + vanillaId + ")" : currentFile;
		return Component.literal(prefix + ": " + shown);
	}

	private static String nextSoundChoice(String current) {
		List<String> files = TradeHighlightConfig.availableSoundFiles();
		// choices are: "" (vanilla), then each file in the folder, in a loop
		int index = files.indexOf(current);
		if (current.isBlank() || index < 0) {
			return files.isEmpty() ? "" : files.get(0);
		}
		return (index + 1 < files.size()) ? files.get(index + 1) : "";
	}

	@Override
	protected void init() {
		TradeHighlightConfig cfg = TradeHighlightConfig.get();
		int cx = this.width / 2;

		// Row 1: sound on/off, maxed on/off
		Component soundLabel = Component.literal("Sound when a highlighted trade appears");
		addRenderableWidget(Button.builder(onOff(soundLabel, cfg.soundEnabled), b -> {
			cfg.soundEnabled = !cfg.soundEnabled;
			b.setMessage(onOff(soundLabel, cfg.soundEnabled));
		}).bounds(cx - 154, 26, 308, 20).build());

		Component maxedLabel = Component.literal("Green border for maxed-out books");
		addRenderableWidget(Button.builder(onOff(maxedLabel, cfg.highlightMaxed), b -> {
			cfg.highlightMaxed = !cfg.highlightMaxed;
			b.setMessage(onOff(maxedLabel, cfg.highlightMaxed));
		}).bounds(cx - 154, 48, 308, 20).build());

		// Row 2: max price / volume
		addRenderableWidget(Button.builder(Component.literal("Max price (emeralds)"), b -> {})
			.bounds(cx - 154, 74, 150, 20).build()).active = false;
		maxPriceBox = new EditBox(this.font, cx - 4, 74, 158, 20, Component.literal("Max price"));
		maxPriceBox.setValue(Integer.toString(cfg.maxPrice));
		maxPriceBox.setResponder(v -> {
			try { cfg.maxPrice = Math.max(0, Integer.parseInt(v.trim())); } catch (NumberFormatException ignored) {}
		});
		addRenderableWidget(maxPriceBox);

		addRenderableWidget(Button.builder(Component.literal("Volume 0.0 - 1.0"), b -> {})
			.bounds(cx - 154, 98, 150, 20).build()).active = false;
		volumeBox = new EditBox(this.font, cx - 4, 98, 158, 20, Component.literal("Volume"));
		volumeBox.setValue(String.format(java.util.Locale.ROOT, "%.2f", cfg.soundVolume));
		volumeBox.setResponder(v -> {
			try { cfg.soundVolume = Math.clamp(Float.parseFloat(v.trim()), 0.0F, 1.0F); } catch (NumberFormatException ignored) {}
		});
		addRenderableWidget(volumeBox);

		// Row 3: HEX colors
		addRenderableWidget(Button.builder(Component.literal("Gold HEX").withStyle(ChatFormatting.GOLD), b -> {})
			.bounds(cx - 154, 122, 150, 20).build()).active = false;
		goldColorBox = new EditBox(this.font, cx - 4, 122, 158, 20, Component.literal("Gold HEX"));
		goldColorBox.setMaxLength(7);
		goldColorBox.setValue(cfg.goldColorHex);
		goldColorBox.setResponder(v -> cfg.goldColorHex = v.trim());
		addRenderableWidget(goldColorBox);

		addRenderableWidget(Button.builder(Component.literal("Green HEX").withStyle(ChatFormatting.GREEN), b -> {})
			.bounds(cx - 154, 146, 150, 20).build()).active = false;
		greenColorBox = new EditBox(this.font, cx - 4, 146, 158, 20, Component.literal("Green HEX"));
		greenColorBox.setMaxLength(7);
		greenColorBox.setValue(cfg.greenColorHex);
		greenColorBox.setResponder(v -> cfg.greenColorHex = v.trim());
		addRenderableWidget(greenColorBox);

		// Row 4: trade sound file (cycles through config/tradehighlight/sounds/)
		addRenderableWidget(Button.builder(soundLabel("Trade sound", cfg.tradeSoundFile, cfg.soundId), b -> {
			cfg.tradeSoundFile = nextSoundChoice(cfg.tradeSoundFile);
			b.setMessage(soundLabel("Trade sound", cfg.tradeSoundFile, cfg.soundId));
		}).bounds(cx - 154, 170, 308, 20).build());

		// Row 5: double-break protection
		Component protLabel = Component.literal("Double-break protection (lectern misclick)");
		addRenderableWidget(Button.builder(onOff(protLabel, cfg.doubleBreakProtection), b -> {
			cfg.doubleBreakProtection = !cfg.doubleBreakProtection;
			b.setMessage(onOff(protLabel, cfg.doubleBreakProtection));
		}).bounds(cx - 154, 194, 308, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Protected block ID"), b -> {})
			.bounds(cx - 154, 218, 150, 20).build()).active = false;
		protectedBlockBox = new EditBox(this.font, cx - 4, 218, 158, 20, Component.literal("Protected block"));
		protectedBlockBox.setValue(cfg.protectedBlockId);
		protectedBlockBox.setResponder(v -> cfg.protectedBlockId = v.trim());
		addRenderableWidget(protectedBlockBox);

		addRenderableWidget(Button.builder(soundLabel("Warning sound", cfg.warningSoundFile, cfg.warningSoundId), b -> {
			cfg.warningSoundFile = nextSoundChoice(cfg.warningSoundFile);
			b.setMessage(soundLabel("Warning sound", cfg.warningSoundFile, cfg.warningSoundId));
		}).bounds(cx - 154, 242, 308, 20).build());

		// Enchantment toggles (gold border), paged
		List<String> all = TradeHighlightConfig.VANILLA_ENCHANTMENTS;
		int pages = (all.size() + PER_PAGE - 1) / PER_PAGE;
		int start = page * PER_PAGE;
		int listTop = 292;
		for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
			String name = all.get(start + i);
			String id = "minecraft:" + name;
			Component label = Component.translatable("enchantment.minecraft." + name);
			int col = i % 2;
			int row = i / 2;
			int x = cx - 154 + col * 156;
			int y = listTop + row * 22;
			addRenderableWidget(Button.builder(onOff(label, cfg.enchantments.contains(id)), b -> {
				if (!cfg.enchantments.remove(id)) {
					cfg.enchantments.add(id);
				}
				b.setMessage(onOff(label, cfg.enchantments.contains(id)));
			}).bounds(x, y, 152, 20).build());
		}

		// Footer: previous / next / done
		int fy = this.height - 28;
		Button prev = Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(cx - 154, fy, 40, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);

		Button next = Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(cx - 110, fy, 40, 20).build();
		next.active = page < pages - 1;
		addRenderableWidget(next);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(cx + 4, fy, 150, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 12, 0xFFFFFFFF, true);

		int pages = (TradeHighlightConfig.VANILLA_ENCHANTMENTS.size() + PER_PAGE - 1) / PER_PAGE;
		Component info = Component.literal("Gold border: selected enchantments   (page " + (page + 1) + "/" + pages + ")");
		graphics.drawString(this.font, info, this.width / 2 - this.font.width(info) / 2, 280, 0xFFAAAAAA, false);

		Component hint = Component.literal("Sound buttons cycle through config/tradehighlight/sounds/ (.wav / .ogg)");
		graphics.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 266, 0xFF888888, false);
	}

	@Override
	public void onClose() {
		TradeHighlightConfig.save();
		this.minecraft.setScreen(parent);
	}
}
