package com.xuqor.tradehighlight.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xuqor.tradehighlight.TradeHighlight;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Simple JSON config stored in config/tradehighlight.json. */
public class TradeHighlightConfig {

	/** Vanilla enchantments shown in the config screen (ids without the "minecraft:" prefix). */
	public static final List<String> VANILLA_ENCHANTMENTS = List.of(
		"mending", "unbreaking", "efficiency", "fortune", "silk_touch",
		"protection", "fire_protection", "blast_protection", "projectile_protection", "feather_falling",
		"respiration", "aqua_affinity", "thorns", "depth_strider", "frost_walker",
		"soul_speed", "swift_sneak", "sharpness", "smite", "bane_of_arthropods",
		"knockback", "fire_aspect", "looting", "sweeping_edge", "power",
		"punch", "flame", "infinity", "luck_of_the_sea", "lure",
		"loyalty", "impaling", "riptide", "channeling", "multishot",
		"quick_charge", "piercing", "density", "breach", "wind_burst"
	);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Play a sound when a highlighted trade shows up. */
	public boolean soundEnabled = true;

	/** Draw a green border on books with a maxed-out enchantment. */
	public boolean highlightMaxed = true;

	/** Enchantments that get a GOLD border (full ids, e.g. "minecraft:mending"). Modded ids can be added by hand. */
	public Set<String> enchantments = new LinkedHashSet<>(Set.of("minecraft:mending"));

	private static TradeHighlightConfig instance = new TradeHighlightConfig();

	public static TradeHighlightConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("tradehighlight.json");
	}

	public static void load() {
		Path file = path();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				TradeHighlightConfig loaded = GSON.fromJson(reader, TradeHighlightConfig.class);
				if (loaded != null) {
					if (loaded.enchantments == null) loaded.enchantments = new LinkedHashSet<>();
					instance = loaded;
				}
			} catch (Exception e) {
				TradeHighlight.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		save(); // writes the file on first run and normalizes it afterwards
	}

	public static void save() {
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			TradeHighlight.LOGGER.warn("Could not save config", e);
		}
	}
}
