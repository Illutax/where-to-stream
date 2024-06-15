package tech.dobler.werstreamt.services.mappers;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domainvalues.AvailabilityType;
import tech.dobler.werstreamt.domainvalues.Price;
import tech.dobler.werstreamt.entities.Availability;
import tech.dobler.werstreamt.entities.QueryResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryResultMapperTest {

    @Test
    void name() {
        // Arrange
        final var availability = new Availability(AvailabilityType.RENT, new Price("1.99 €"), null, new Price("9.99 €"));
        final var pojo = new QueryResult("tt0123755", "Cube", true, List.of(availability));

        // Act
        final var dto = QueryResultMapper.INSTANCE.entityToDto(pojo);
        final var back = QueryResultMapper.INSTANCE.dtoToEntity(dto);

        assertThat(dto).isNotNull();
        assertThat(back).isNotNull();
        assertThat(back).isEqualTo(pojo);
    }
}