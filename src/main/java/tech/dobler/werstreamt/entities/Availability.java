package tech.dobler.werstreamt.entities;

import tech.dobler.werstreamt.domainvalues.AvailabilityType;
import tech.dobler.werstreamt.domainvalues.Price;

public record Availability(AvailabilityType type, Price sd, Price hd, Price fourK) {
}
