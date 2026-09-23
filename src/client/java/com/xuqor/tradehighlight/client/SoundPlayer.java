package com.xuqor.tradehighlight.client;

import com.xuqor.tradehighlight.TradeHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;

/**
 * Plays the mod's two alert sounds (trade found / misclick warning): either a vanilla SoundEvent
 * through Minecraft's own sound system, or a .wav / .ogg file from config/tradehighlight/sounds/
 * (see TradeHighlightConfig.soundsDir()), picked in the config screen. Custom files are decoded
 * and played through javax.sound.sampled so nothing needs to be registered with the game; .ogg
 * files are decoded with LWJGL's STBVorbis, the same library Minecraft itself uses for .ogg.
 */
final class SoundPlayer {

	private SoundPlayer() {}

	static void playAlert() {
		TradeHighlightConfig cfg = TradeHighlightConfig.get();
		playOne(cfg.tradeSoundFile, cfg.soundId, cfg.soundVolume);
	}

	static void playWarning() {
		TradeHighlightConfig cfg = TradeHighlightConfig.get();
		playOne(cfg.warningSoundFile, cfg.warningSoundId, cfg.soundVolume);
	}

	private static void playOne(String fileName, String vanillaId, float volume) {
		if (fileName != null && !fileName.isBlank()) {
			playCustomFile(TradeHighlightConfig.soundsDir().resolve(fileName).toFile(), volume, vanillaId);
		} else {
			playVanilla(vanillaId, volume);
		}
	}

	private static void playVanilla(String soundId, float volume) {
		SoundEvent event = SoundEvents.PLAYER_LEVELUP;
		Identifier id = Identifier.tryParse(soundId);
		if (id != null) {
			SoundEvent found = BuiltInRegistries.SOUND_EVENT.getValue(id);
			if (found != null) event = found;
		}
		Minecraft.getInstance().getSoundManager()
			.play(SimpleSoundInstance.forUI(event, 1.0F, Math.clamp(volume, 0.0F, 1.0F)));
	}

	/** Decoding + opening the line can take a moment, so this runs off the render thread. */
	private static void playCustomFile(File file, float volume, String vanillaFallbackId) {
		Thread thread = new Thread(() -> {
			if (!file.isFile()) {
				TradeHighlight.LOGGER.warn("Custom sound not found: {}, falling back to vanilla sound", file);
				playVanilla(vanillaFallbackId, volume);
				return;
			}
			try {
				String name = file.getName().toLowerCase();
				if (name.endsWith(".ogg")) {
					playOgg(file, volume);
				} else {
					playWav(file, volume);
				}
			} catch (Exception e) {
				TradeHighlight.LOGGER.warn("Could not play custom sound {}, falling back to vanilla sound", file, e);
				playVanilla(vanillaFallbackId, volume);
			}
		}, "tradehighlight-sound");
		thread.setDaemon(true);
		thread.start();
	}

	private static void playWav(File file, float volume) throws Exception {
		try (Clip clip = AudioSystem.getClip()) {
			clip.open(AudioSystem.getAudioInputStream(file));
			applyClipVolume(clip, volume);
			clip.start();
			// Clip plays on its own line; keep this thread alive just long enough for it to finish.
			Thread.sleep(Math.max(200, (int) (clip.getMicrosecondLength() / 1000L) + 200));
		}
	}

	private static void playOgg(File file, float volume) throws Exception {
		byte[] bytes = Files.readAllBytes(file.toPath());
		ByteBuffer packed = BufferUtils.createByteBuffer(bytes.length).put(bytes);
		packed.flip();

		IntBuffer error = BufferUtils.createIntBuffer(1);
		long decoder = STBVorbis.stb_vorbis_open_memory(packed, error, null);
		if (decoder == MemoryUtil.NULL) {
			throw new IOException("stb_vorbis error " + error.get(0));
		}

		try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
			STBVorbis.stb_vorbis_get_info(decoder, info);
			int channels = info.channels();
			int sampleRate = info.sample_rate();
			int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder) * channels;

			ShortBuffer pcm = MemoryUtil.memAllocShort(totalSamples);
			try {
				int decodedSamples = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
				pcm.position(0).limit(decodedSamples * channels);

				byte[] pcmBytes = new byte[pcm.remaining() * 2];
				ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);

				AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
				try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
					line.open(format);
					applyLineVolume(line, volume);
					line.start();
					line.write(pcmBytes, 0, pcmBytes.length);
					line.drain();
				}
			} finally {
				MemoryUtil.memFree(pcm);
			}
		} finally {
			STBVorbis.stb_vorbis_close(decoder);
		}
	}

	private static void applyClipVolume(Clip clip, float volume) {
		try {
			applyGain((FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN), volume);
		} catch (IllegalArgumentException ignored) {
			// no volume control on this line; play at whatever the file's native level is
		}
	}

	private static void applyLineVolume(SourceDataLine line, float volume) {
		try {
			applyGain((FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN), volume);
		} catch (IllegalArgumentException ignored) {
			// no volume control on this line; play at whatever the file's native level is
		}
	}

	private static void applyGain(FloatControl gain, float volume) {
		float clamped = Math.clamp(volume, 0.0001F, 1.0F); // 0 breaks the log below, treat as near-silent
		float dB = (float) (Math.log10(clamped) * 20.0);
		gain.setValue(Math.clamp(dB, gain.getMinimum(), gain.getMaximum()));
	}
}
