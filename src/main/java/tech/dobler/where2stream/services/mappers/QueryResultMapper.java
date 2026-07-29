package tech.dobler.where2stream.services.mappers;

import lombok.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.dobler.where2stream.domain.QueryResult;
import tech.dobler.where2stream.persistence.QueryResultDB;

@Mapper(componentModel = "spring")
public interface QueryResultMapper {
    @NonNull
    QueryResultMapper INSTANCE = Mappers.getMapper(QueryResultMapper.class);

    QueryResultDB entityToDto(QueryResult queryResult);
    QueryResult dtoToEntity(QueryResultDB db);
}
