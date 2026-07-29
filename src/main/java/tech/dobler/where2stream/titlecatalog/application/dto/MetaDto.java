package tech.dobler.where2stream.titlecatalog.application.dto;

import tech.dobler.where2stream.titlecatalog.domain.AgeRating;

/**
 * Per-title display metadata for a table row, from one shared IMDb fetch: the age rating (for the
 * FSK badge) and the German title (for the German-title toggle). Either field may be null.
 */
public record MetaDto(AgeRating rating, String germanTitle) {
}
