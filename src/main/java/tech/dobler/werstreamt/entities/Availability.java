package tech.dobler.werstreamt.entities;

import tech.dobler.werstreamt.domainvalues.AvailabilityType;

public record Availability(AvailabilityType type, String priceSD, String priceHD, String price4k) {
}
