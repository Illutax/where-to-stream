package tech.dobler.where2stream.streamingavailability.adapter.out.persistence;

import lombok.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.streamingavailability.domain.QueryResultDB;

@Mapper(componentModel = "spring")
public interface QueryResultMapper {
    @NonNull
    QueryResultMapper INSTANCE = Mappers.getMapper(QueryResultMapper.class);

    QueryResultDB entityToDto(QueryResult queryResult);
    QueryResult dtoToEntity(QueryResultDB db);
}
