package com.xuqor.tradehighlight.client.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
	/** Index of the first trade shown in the scrolled list. */
	@Accessor("scrollOff")
	int tradehighlight$getScrollOff();
}
