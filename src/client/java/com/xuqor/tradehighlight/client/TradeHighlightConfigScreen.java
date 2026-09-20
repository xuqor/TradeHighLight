package com.xuqor.tradehighlight.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Config screen opened from Mod Menu: choose which enchanted books glow gold. */
public class TradeHighlightConfigScreen extends Screen {

	private static final int PER_PAGE = 10; // 2 columns x 5 rows

	private final Screen parent;
	private int page = 0;

	public TradeHighlightConfigScreen(Screen parent) {
		super(Component.literal("TradeHighlight"));
		this.parent = parent;
	}

	private static Component onOff(Component label, boolean on) {
		return label.copy().append(Component.literal(on ? ": ON" : ": OFF")
			.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	@Override
	protected void init() {
		TradeHighlightConfig cfg = TradeHighlightConfig.get();
		int cx = this.width / 2;

		// General toggles
		Component soundLabel = Component.literal("Sound when a highlighted trade appears");
		addRenderableWidget(Button.builder(onOff(soundLabel, cfg.soundEnabled), b -> {
			cfg.soundEnabled = !cfg.soundEnabled;
			b.setMessage(onOff(soundLabel, cfg.soundEnabled));
		}).bounds(cx - 154, 28, 308, 20).build());

		Component maxedLabel = Component.literal("Green border for maxed-out books");
		addRenderableWidget(Button.builder(onOff(maxedLabel, cfg.highlightMaxed), b -> {
			cfg.highlightMaxed = !cfg.highlightMaxed;
			b.setMessage(onOff(maxedLabel, cfg.highlightMaxed));
		}).bounds(cx - 154, 52, 308, 20).build());

		// Enchantment toggles (gold border), paged
		List<String> all = TradeHighlightConfig.VANILLA_ENCHANTMENTS;
		int pages = (all.size() + PER_PAGE - 1) / PER_PAGE;
		int start = page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
			String name = all.get(start + i);
			String id = "minecraft:" + name;
			Component label = Component.translatable("enchantment.minecraft." + name);
			int col = i % 2;
			int row = i / 2;
			int x = cx - 154 + col * 156;
			int y = 82 + row * 22;
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
		graphics.drawString(this.font, info, this.width / 2 - this.font.width(info) / 2, 72 - 0, 0xFFAAAAAA, false);
	}

	@Override
	public void onClose() {
		TradeHighlightConfig.save();
		this.minecraft.setScreen(parent);
	}
}
