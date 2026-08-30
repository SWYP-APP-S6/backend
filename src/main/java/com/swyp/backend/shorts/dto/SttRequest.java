package com.swyp.backend.shorts.dto;

public record SttRequest(String model, String initialPrompt, String language, boolean force) {
}
