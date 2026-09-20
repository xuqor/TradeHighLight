package com.xuqor.tradehighlight.client;

import com.xuqor.tradehighlight.client.mixin.MerchantScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Highlights the best trades in the villager trading screen:
 *  - GOLD border:  enchanted book with an enchantment you selected (default: Mending)
 *  - GREEN border: enchanted book with a maxed-out enchantment (optional). Curses are ignored.
 * Plays a sound when a highlighted trade shows up. Purely client-side.
 */
public class TradeHighlightClient implements ClientModInitializer {

	private static final int GOLD = 0xFFFFD700;
	private static final int GREEN = 0xFF55FF55;

	// The trade buttons in MerchantScreen are 88 x 20 pixels.
	private static final int BUTTON_W = 88;
	private static final int BUTTON_H = 20;

	/** Remembers how many highlighted trades we have already announced for one open screen. */
	private static final class SoundState {
		int lastCount = 0;
	}

	@Override
	public void onInitializeClient() {
		TradeHighlightConfig.load();

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof MerchantScreen) {
				SoundState state = new SoundState();
				ScreenEvents.afterRender(screen).register(TradeHighlightClient::render);
				ScreenEvents.afterTick(screen).register(s -> tick(s, state));
			}
		});
	}

	private static void tick(Screen screen, SoundState state) {
		if (!(screen instanceof MerchantScreen merchantScreen)) return;

		int count = 0;
		for (MerchantOffer offer : merchantScreen.getMenu().getOffers()) {
			if (classify(offer) != 0) count++;
		}
		if (count > state.lastCount && TradeHighlightConfig.get().soundEnabled) {
			Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
		}
		state.lastCount = count;
	}

	private static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
		if (!(screen instanceof MerchantScreen merchantScreen)) return;

		MerchantOffers offers = merchantScreen.getMenu().getOffers();
		int scroll = ((MerchantScreenAccessor) merchantScreen).tradehighlight$getScrollOff();

		int slot = 0; // position of the button in the visible list (0..6)
		for (GuiEventListener child : merchantScreen.children()) {
			if (!(child instanceof AbstractWidget button)
					|| button.getWidth() != BUTTON_W || button.getHeight() != BUTTON_H) {
				continue;
			}
			int offerIndex = scroll + slot;
			slot++;

			if (!button.visible || offerIndex >= offers.size()) continue;

			int color = classify(offers.get(offerIndex));
			if (color != 0) {
				// two nested outlines make the border 2 px thick
				graphics.renderOutline(button.getX() - 1, button.getY() - 1, BUTTON_W + 2, BUTTON_H + 2, color);
				graphics.renderOutline(button.getX(), button.getY(), BUTTON_W, BUTTON_H, color);
			}
		}
	}

	/** Returns a border color, or 0 if the trade is not special. */
	private static int classify(MerchantOffer offer) {
		if (offer.isOutOfStock()) return 0;

		ItemStack result = offer.getResult();
		if (!result.is(Items.ENCHANTED_BOOK)) return 0;

		ItemEnchantments enchantments = result.get(DataComponents.STORED_ENCHANTMENTS);
		if (enchantments == null) return 0;

		TradeHighlightConfig cfg = TradeHighlightConfig.get();
		boolean maxed = false;
		for (var entry : enchantments.entrySet()) {
			Holder<Enchantment> holder = entry.getKey();
			if (holder.is(EnchantmentTags.CURSE)) continue;

			if (cfg.enchantments.contains(holder.getRegisteredName())) return GOLD;

			int max = holder.value().getMaxLevel();
			if (holder.is(Enchantments.SILK_TOUCH) || (max >= 3 && entry.getIntValue() >= max)) {
				maxed = true;
			}
		}
		return (cfg.highlightMaxed && maxed) ? GREEN : 0;
	}
}
