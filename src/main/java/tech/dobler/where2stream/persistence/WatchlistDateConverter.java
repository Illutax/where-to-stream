package tech.dobler.where2stream.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tech.dobler.where2stream.domain.WatchlistDate;

/** Maps {@link WatchlistDate} to its {@code varchar} column and back (auto-applied to every field). */
@Converter(autoApply = true)
public class WatchlistDateConverter implements AttributeConverter<WatchlistDate, String> {

    @Override
    public String convertToDatabaseColumn(WatchlistDate attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public WatchlistDate convertToEntityAttribute(String dbData) {
        return dbData == null ? null : WatchlistDate.of(dbData);
    }
}
