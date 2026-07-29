package tech.dobler.where2stream.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record Price(String value) {
}
