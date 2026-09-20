package com.xuqor.tradehighlight;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeHighlight implements ModInitializer {
	public static final String MOD_ID = "tradehighlight";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Nothing here: everything happens on the client, see TradeHighlightClient.
	}
}
