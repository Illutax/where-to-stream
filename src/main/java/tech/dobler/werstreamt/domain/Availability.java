package tech.dobler.werstreamt.domain;

import jakarta.persistence.*;
import tech.dobler.werstreamt.domain.AvailabilityType;
import tech.dobler.werstreamt.domain.Price;

@Embeddable
public record Availability(
        @Column(name="type")
        @Enumerated(EnumType.STRING) AvailabilityType type,
        @AttributeOverride(name = "value", column = @Column(name = "sd")) Price sd,
        @AttributeOverride(name = "value", column = @Column(name = "hd")) Price hd,
        @AttributeOverride(name = "value", column = @Column(name = "fourK")) Price fourK
) {
}
