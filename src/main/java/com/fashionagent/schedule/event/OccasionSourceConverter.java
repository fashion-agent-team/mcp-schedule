package com.fashionagent.schedule.event;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class OccasionSourceConverter implements AttributeConverter<OccasionSource, String> {

    @Override
    public String convertToDatabaseColumn(OccasionSource v) {
        return v == null ? null : v.dbValue();
    }

    @Override
    public OccasionSource convertToEntityAttribute(String v) {
        return v == null ? null : OccasionSource.fromDb(v);
    }
}
