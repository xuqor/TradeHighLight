package com.xuqor.tradehighlight.client;

import com.xuqor.tradehighlight.client.mixin.AbstractContainerScreenAccessor;
import com.xuqor.tradehighlight.client.mixin.MerchantScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;

/**
 * Highlights the best trades in the villager trading screen:
 *  - GOLD border:  enchanted book with an enchantment you selected (default: Mending)
 *  - GREEN border: enchanted book with a maxed-out enchantment (optional). Curses are ignored.
 * Trades above the configured emerald price are never highlighted, whatever the enchantment.
 * Plays an alert sound (vanilla or a file from config/tradehighlight/sounds/) when a highlighted
 * trade shows up, and shows a "Rerolls: N" counter for how many times you've reopened the same
 * villager while looking. Also protects the job-site block (e.g. the lectern) from a misclick:
 * while a highlighted trade is on offer, the first hit on that block is cancelled with a warning
 * sound; the second hit breaks it as normal. Purely client-side.
 */
public class TradeHighlightClient implements ClientModInitializer {

	// The trade buttons in MerchantScreen are 88 x 20 pixels.
	private static final int BUTTON_W = 88;
	private static final int BUTTON_H = 20;

	/** Remembers how many highlighted trades we have already announced for one open screen. */
	private static final class SoundState {
		int lastCount = 0;
	}

	/** True while the last-opened merchant screen has at least one highlighted trade right now. */
	private static volatile boolean guarded = false;

	/** First-hit-cancelled state for the double-break protection, reset a moment after each cancel. */
	private static volatile boolean armed = false;

	// --- reroll counter ---
	private static WeakReference<Merchant> lastTrader = null;
	private static int rerollCount = 0;

	@Override
	public void onInitializeClient() {
		TradeHighlightConfig.load();

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof MerchantScreen merchantScreen) {
				trackReroll(merchantScreen);
				SoundState state = new SoundState();
				ScreenEvents.afterRender(screen).register(TradeHighlightClient::render);
				ScreenEvents.afterTick(screen).register(s -> tick(s, state));
			}
		});

		AttackBlockCallback.EVENT.register(TradeHighlightClient::onAttackBlock);
	}

	private static void trackReroll(MerchantScreen screen) {
		Merchant trader = screen.getMenu().getTrader();
		Merchant previous = lastTrader == null ? null : lastTrader.get();
		if (trader == previous) {
			rerollCount++;
		} else {
			lastTrader = new WeakReference<>(trader);
			rerollCount = 1;
		}
	}

	private static void tick(Screen screen, SoundState state) {
		if (!(screen instanceof MerchantScreen merchantScreen)) return;

		int count = 0;
		for (MerchantOffer offer : merchantScreen.getMenu().getOffers()) {
			if (classify(offer) != 0) count++;
		}
		if (count > state.lastCount && TradeHighlightConfig.get().soundEnabled) {
			SoundPlayer.playAlert();
		}
		state.lastCount = count;

		if (count > 0) {
			rerollCount = 0; // found what we were rerolling for
		}

		guarded = count > 0;
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

		if (rerollCount > 0) {
			AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) merchantScreen;
			Component text = Component.literal("Rerolls: " + rerollCount).withStyle(ChatFormatting.GRAY);
			graphics.drawString(Minecraft.getInstance().font, text,
				pos.tradehighlight$getLeftPos(), pos.tradehighlight$getTopPos() - 10, 0xFFAAAAAA, true);
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
		if (emeraldPrice(offer) > cfg.maxPrice) return 0;

		boolean maxed = false;
		for (var entry : enchantments.entrySet()) {
			Holder<Enchantment> holder = entry.getKey();
			if (holder.is(EnchantmentTags.CURSE)) continue;

			if (cfg.enchantments.contains(holder.getRegisteredName())) return cfg.goldColor();

			int max = holder.value().getMaxLevel();
			if (holder.is(Enchantments.SILK_TOUCH) || (max >= 3 && entry.getIntValue() >= max)) {
				maxed = true;
			}
		}
		return (cfg.highlightMaxed && maxed) ? cfg.greenColor() : 0;
	}

	/** Total emeralds the player pays for this offer (costA + costB, whichever side is emeralds). */
	private static int emeraldPrice(MerchantOffer offer) {
		int total = 0;
		if (offer.getCostA().is(Items.EMERALD)) total += offer.getCostA().getCount();
		ItemStack costB = offer.getCostB();
		if (!costB.isEmpty() && costB.is(Items.EMERALD)) total += costB.getCount();
		return total;
	}

	// --- double-break protection ---

	private static InteractionResult onAttackBlock(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.level.Level level, net.minecraft.world.InteractionHand hand,
			net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
		TradeHighlightConfig cfg = TradeHighlightConfig.get();
		if (!cfg.doubleBreakProtection || !guarded) return InteractionResult.PASS;
		if (!level.isClientSide) return InteractionResult.PASS;

		ResourceLocation protectedId = ResourceLocation.tryParse(cfg.protectedBlockId);
		if (protectedId == null) return InteractionResult.PASS;

		Block block = BuiltInRegistries.BLOCK.getValue(protectedId);
		BlockState state = level.getBlockState(pos);
		if (block == null || !state.is(block)) return InteractionResult.PASS;

		if (!armed) {
			armed = true;
			player.displayClientMessage(
				Component.literal("A highlighted trade is up for grabs \u2014 hit again to break it.")
					.withStyle(ChatFormatting.GOLD), true);
			SoundPlayer.playWarning();
			// only guard the very next swing; if the player just moves on, don't keep blocking forever
			scheduleDisarm();
			return InteractionResult.FAIL;
		}

		armed = false;
		guarded = false; // this break is intentional and about to go through; stop protecting
		return InteractionResult.PASS;
	}

	private static void scheduleDisarm() {
		Thread timeout = new Thread(() -> {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException ignored) {
				return;
			}
			armed = false;
		}, "tradehighlight-disarm");
		timeout.setDaemon(true);
		timeout.start();
	}
}
