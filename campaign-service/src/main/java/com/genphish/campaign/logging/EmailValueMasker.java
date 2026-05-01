package com.genphish.campaign.logging;

import com.fasterxml.jackson.core.JsonStreamContext;
import net.logstash.logback.mask.ValueMasker;

import java.util.regex.Pattern;

public class EmailValueMasker implements ValueMasker {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
    );

    @Override
    public Object mask(JsonStreamContext context, Object value) {
        if (!(value instanceof String text)) {
            return value;
        }

        if (!EMAIL_PATTERN.matcher(text).find()) {
            return value;
        }

        return EMAIL_PATTERN.matcher(text).replaceAll("***@***.***");
    }
}
