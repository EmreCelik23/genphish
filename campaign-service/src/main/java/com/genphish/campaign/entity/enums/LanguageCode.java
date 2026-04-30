package com.genphish.campaign.entity.enums;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

@Slf4j
public enum LanguageCode {
    TR,
    EN;

    public static LanguageCode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return TR;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("EN")) {
            return EN;
        } else if (!normalized.startsWith("TR")) {
            log.warn("Unrecognized language code '{}', defaulting to TR", value);
        }
        return TR;
    }
}
