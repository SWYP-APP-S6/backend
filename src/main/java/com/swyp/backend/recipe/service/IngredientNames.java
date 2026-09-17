package com.swyp.backend.recipe.service;

import java.util.regex.Pattern;

public final class IngredientNames {

	private static final Pattern HANGUL = Pattern.compile("[가-힣]");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final int MAX_LENGTH = 64;

	private IngredientNames() {
	}

	public static String normKeyOf(String name) {
		if (name == null || !HANGUL.matcher(name).find()) {
			return "";
		}
		String squeezed = WHITESPACE.matcher(name).replaceAll("").toLowerCase();
		return squeezed.length() > MAX_LENGTH ? squeezed.substring(0, MAX_LENGTH) : squeezed;
	}
}
