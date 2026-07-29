package tech.dobler.where2stream.application.dto;

import java.util.List;

public record ManagePageDto(
        List<ManageRowDto> rows,
        int needsScrapeCount
) {
}
