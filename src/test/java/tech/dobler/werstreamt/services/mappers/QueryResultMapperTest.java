package tech.dobler.werstreamt.services.mappers;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domainvalues.AvailabilityType;
import tech.dobler.werstreamt.domainvalues.Price;
import tech.dobler.werstreamt.entities.Availability;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.persistence.QueryResultDB;

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

        // Forward mapping carries every field...
        assertThat(dto)
                .extracting(
                        QueryResultDB::getImdbId,
                        QueryResultDB::getStreamingServiceName,
                        QueryResultDB::isFlatrate,
                        QueryResultDB::getAvailabilities)
                .containsExactly(
                        pojo.imdbId(),
                        pojo.streamingServiceName(),
                        pojo.flatrate(),
                        pojo.availabilities());
        // ...and the round trip reproduces the original.
        assertThat(back).isEqualTo(pojo);
    }
}