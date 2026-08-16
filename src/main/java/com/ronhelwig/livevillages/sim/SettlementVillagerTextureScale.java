package com.ronhelwig.livevillages.sim;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.resources.Identifier;

import com.ronhelwig.livevillages.LiveVillages;

/**
 * Per-world villager / illager atlas scale. Vanilla and Live Villages
 * overlays are authored at 64x64. Optional 128 and 256 sheets keep the
 * same UV layout with more pixels per face.
 */
public final class SettlementVillagerTextureScale {
	public static final int DEFAULT_SCALE = 64;
	public static final int SCALE_128 = 128;
	public static final int SCALE_256 = 256;
	public static final Option NEW_WORLD_DEFAULT = Option.PIXELS_256;
	private static final String ENTITY_PREFIX = "textures/entity/";
	private static final ArgumentType<Option> OPTION_ARGUMENT = new OptionArgumentType();

	private SettlementVillagerTextureScale() {
	}

	public static ArgumentType<Option> optionArgumentType() {
		return OPTION_ARGUMENT;
	}

	public enum Option {
		PIXELS_64(DEFAULT_SCALE),
		PIXELS_128(SCALE_128),
		PIXELS_256(SCALE_256);

		private final int pixels;

		Option(int pixels) {
			this.pixels = pixels;
		}

		public int pixels() {
			return pixels;
		}

		@Override
		public String toString() {
			return Integer.toString(pixels);
		}
	}

	private static final class OptionArgumentType implements ArgumentType<Option> {
		private static final List<String> EXAMPLES = List.of("64", "128", "256");
		private static final DynamicCommandExceptionType INVALID_VALUE = new DynamicCommandExceptionType(
			value -> () -> "Expected one of 64, 128, or 256; found " + value
		);

		@Override
		public Option parse(StringReader reader) throws CommandSyntaxException {
			String value = reader.readUnquotedString();
			return switch (value) {
				case "64" -> Option.PIXELS_64;
				case "128" -> Option.PIXELS_128;
				case "256" -> Option.PIXELS_256;
				default -> throw INVALID_VALUE.createWithContext(reader, value);
			};
		}

		@Override
		public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
			for (String example : EXAMPLES) {
				if (example.startsWith(builder.getRemaining())) {
					builder.suggest(example);
				}
			}
			return builder.buildFuture();
		}

		@Override
		public Collection<String> getExamples() {
			return EXAMPLES;
		}
	}

	public static int sanitize(int scale) {
		if (scale >= 192) {
			return SCALE_256;
		}
		if (scale >= 96) {
			return SCALE_128;
		}
		return DEFAULT_SCALE;
	}

	public static Option optionForScale(int scale) {
		return switch (sanitize(scale)) {
			case SCALE_128 -> Option.PIXELS_128;
			case SCALE_256 -> Option.PIXELS_256;
			default -> Option.PIXELS_64;
		};
	}

	public static boolean isDefault(int scale) {
		return sanitize(scale) == DEFAULT_SCALE;
	}

	public static Identifier remap(Identifier original, int scale) {
		int sanitized = sanitize(scale);
		if (original == null || sanitized == DEFAULT_SCALE) {
			return original;
		}

		String path = original.getPath();
		if (!path.startsWith(ENTITY_PREFIX)) {
			return original;
		}

		String rest = path.substring(ENTITY_PREFIX.length());
		if (!rest.startsWith("villager/")
			&& !rest.startsWith("zombie_villager/")
			&& !rest.startsWith("illager/")) {
			return original;
		}

		return Identifier.fromNamespaceAndPath(
			LiveVillages.MOD_ID,
			ENTITY_PREFIX + "scale" + sanitized + "/" + rest
		);
	}
}
