package tech.dobler.where2stream.streamingavailability.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record Price(String value) {
}
