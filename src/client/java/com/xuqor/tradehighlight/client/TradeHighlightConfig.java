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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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

	private static final int DEFAULT_GOLD = 0xFFD700;
	private static final int DEFAULT_GREEN = 0x55FF55;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Play a sound when a highlighted trade shows up. */
	public boolean soundEnabled = true;

	/** Draw a green border on books with a maxed-out enchantment. */
	public boolean highlightMaxed = true;

	/** Enchantments that get a GOLD border (full ids, e.g. "minecraft:mending"). Modded ids can be added by hand. */
	public Set<String> enchantments = new LinkedHashSet<>(Set.of("minecraft:mending"));

	/** Trades costing more emeralds than this are never highlighted, no matter the enchantment. */
	public int maxPrice = 20;

	/** Border colors as 6-digit hex, no '#'. Falls back to the gold/green defaults if unparsable. */
	public String goldColorHex = "FFD700";
	public String greenColorHex = "55FF55";

	/** Shared volume for both alert sounds, 0.0 - 1.0. */
	public float soundVolume = 1.0f;

	/** Vanilla sound for a highlighted trade, used only when tradeSoundFile is empty. */
	public String soundId = "minecraft:entity.player.levelup";
	/** File name (not full path) inside config/tradehighlight/sounds/, e.g. "ding.ogg". Empty = use soundId. */
	public String tradeSoundFile = "";

	/** Cancel the first break attempt on the job-site block while a highlighted trade is on offer. */
	public boolean doubleBreakProtection = true;
	public String protectedBlockId = "minecraft:lectern";
	/** Vanilla warning sound for a blocked misclick, used only when warningSoundFile is empty. */
	public String warningSoundId = "minecraft:entity.villager.no";
	/** File name (not full path) inside config/tradehighlight/sounds/. Empty = use warningSoundId. */
	public String warningSoundFile = "";

	private static TradeHighlightConfig instance = new TradeHighlightConfig();

	public static TradeHighlightConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("tradehighlight.json");
	}

	/** config/tradehighlight/sounds/ - drop .wav or .ogg files here to use them in the config screen. */
	public static Path soundsDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("tradehighlight").resolve("sounds");
	}

	/** File names (not paths) of every .wav/.ogg file currently in the sounds folder, sorted. */
	public static List<String> availableSoundFiles() {
		List<String> names = new ArrayList<>();
		try (Stream<Path> files = Files.list(soundsDir())) {
			files.map(p -> p.getFileName().toString())
				.filter(n -> n.toLowerCase().endsWith(".wav") || n.toLowerCase().endsWith(".ogg"))
				.sorted(Comparator.naturalOrder())
				.forEach(names::add);
		} catch (IOException ignored) {
			// folder not created yet or unreadable; just report no custom sounds
		}
		return names;
	}

	public static void load() {
		try {
			Files.createDirectories(soundsDir());
		} catch (IOException e) {
			TradeHighlight.LOGGER.warn("Could not create sounds folder", e);
		}

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

	/** Gold border color, ARGB with full alpha. */
	public int goldColor() {
		return parseHex(goldColorHex, DEFAULT_GOLD);
	}

	/** Green border color, ARGB with full alpha. */
	public int greenColor() {
		return parseHex(greenColorHex, DEFAULT_GREEN);
	}

	private static int parseHex(String hex, int fallback) {
		if (hex == null) return 0xFF000000 | fallback;
		String cleaned = hex.trim();
		if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
		try {
			if (cleaned.length() != 6) throw new NumberFormatException();
			return 0xFF000000 | Integer.parseInt(cleaned, 16);
		} catch (NumberFormatException e) {
			return 0xFF000000 | fallback;
		}
	}
}
