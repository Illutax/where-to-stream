package tech.dobler.where2stream.streamingavailability.domain;

import jakarta.persistence.*;
import tech.dobler.where2stream.streamingavailability.domain.AvailabilityType;
import tech.dobler.where2stream.streamingavailability.domain.Price;

@Embeddable
public record Availability(
        @Column(name="type")
        @Enumerated(EnumType.STRING) AvailabilityType type,
        @AttributeOverride(name = "value", column = @Column(name = "sd")) Price sd,
        @AttributeOverride(name = "value", column = @Column(name = "hd")) Price hd,
        @AttributeOverride(name = "value", column = @Column(name = "fourK")) Price fourK
) {
}
