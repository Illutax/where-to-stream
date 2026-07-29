package tech.dobler.where2stream.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tech.dobler.where2stream.domain.ImdbId;

/**
 * Maps {@link ImdbId} to its {@code varchar} column and back. {@code autoApply = true} so every
 * {@code ImdbId} entity attribute uses it without per-field annotations — the columns stay plain
 * strings.
 */
@Converter(autoApply = true)
public class ImdbIdConverter implements AttributeConverter<ImdbId, String> {

    @Override
    public String convertToDatabaseColumn(ImdbId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ImdbId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new ImdbId(dbData);
    }
}
