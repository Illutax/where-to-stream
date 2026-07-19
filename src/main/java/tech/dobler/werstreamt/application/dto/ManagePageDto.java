package tech.dobler.werstreamt.application.dto;

import java.util.List;

public record ManagePageDto(
        List<ManageRowDto> rows,
        int needsScrapeCount
) {
}
