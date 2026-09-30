package com.vikisol.arena.profile.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Stores an Industry as its key (the same strings the enum stored before V44).
@Converter(autoApply = true)
public class IndustryConverter implements AttributeConverter<Industry, String> {

    @Override
    public String convertToDatabaseColumn(Industry industry) {
        return industry == null ? null : industry.name();
    }

    @Override
    public Industry convertToEntityAttribute(String key) {
        return key == null ? null : Industry.of(key);
    }
}
