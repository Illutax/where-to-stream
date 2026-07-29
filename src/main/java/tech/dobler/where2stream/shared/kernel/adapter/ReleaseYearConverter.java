package tech.dobler.where2stream.shared.kernel.adapter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;

/** Maps {@link ReleaseYear} to its {@code integer} column and back (auto-applied to every field). */
@Converter(autoApply = true)
public class ReleaseYearConverter implements AttributeConverter<ReleaseYear, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ReleaseYear attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ReleaseYear convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : ReleaseYear.of(dbData);
    }
}
