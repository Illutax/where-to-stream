package tech.dobler.werstreamt.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record Price(String value) {
}
