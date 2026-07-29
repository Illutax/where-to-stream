package tech.dobler.where2stream.services.mappers;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.domain.AvailabilityType;
import tech.dobler.where2stream.domain.Price;
import tech.dobler.where2stream.domain.Availability;
import tech.dobler.where2stream.domain.ImdbId;
import tech.dobler.where2stream.domain.QueryResult;
import tech.dobler.where2stream.persistence.QueryResultDB;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryResultMapperTest {

    @Test
    void name() {
        // Arrange
        final var availability = new Availability(AvailabilityType.RENT, new Price("1.99 €"), null, new Price("9.99 €"));
        final var pojo = new QueryResult(ImdbId.of("tt0123755"), "Cube", true, List.of(availability), "Deutsch, Englisch (OV)");

        // Act
        final var dto = QueryResultMapper.INSTANCE.entityToDto(pojo);
        final var back = QueryResultMapper.INSTANCE.dtoToEntity(dto);

        // Forward mapping carries every field...
        assertThat(dto)
                .extracting(
                        QueryResultDB::getImdbId,
                        QueryResultDB::getStreamingServiceName,
                        QueryResultDB::isFlatrate,
                        QueryResultDB::getAvailabilities,
                        QueryResultDB::getLanguages)
                .containsExactly(
                        pojo.imdbId(),
                        pojo.streamingServiceName(),
                        pojo.flatrate(),
                        pojo.availabilities(),
                        pojo.languages());
        // ...and the round trip reproduces the original.
        assertThat(back).isEqualTo(pojo);
    }
}