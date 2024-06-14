package tech.dobler.werstreamt.domainvalues;

import jakarta.persistence.Embeddable;

@Embeddable
public record Price(String value) {
}
