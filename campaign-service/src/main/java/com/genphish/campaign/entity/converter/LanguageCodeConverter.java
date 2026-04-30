package com.genphish.campaign.entity.converter;

import com.genphish.campaign.entity.enums.LanguageCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LanguageCodeConverter implements AttributeConverter<LanguageCode, String> {

    @Override
    public String convertToDatabaseColumn(LanguageCode attribute) {
        if (attribute == null) {
            return LanguageCode.TR.name();
        }
        return attribute.name();
    }

    @Override
    public LanguageCode convertToEntityAttribute(String dbData) {
        return LanguageCode.fromNullable(dbData);
    }
}
