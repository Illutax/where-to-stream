package tech.dobler.werstreamt.application;

import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.AvailabilityType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Formats a list of {@link Availability}s into the human-readable German price string used on
 * the paid ("kaufbar") pages, e.g. {@code "kaufen: HD: 9,99 € , leihen: HD: 3,99 €"}.
 *
 * <p>Formatting lives on the server (not in a template or the Angular client) so both UIs show
 * the identical string.
 */
public final class AvailabilityFormatter {

    private AvailabilityFormatter() {
    }

    public static String prettyPrint(List<Availability> availabilities) {
        return availabilities.stream()
                .map(a -> {
                    final var sb = new StringBuilder();
                    if (a.type() == AvailabilityType.RENT) {
                        sb.append("leihen: ");
                    } else {
                        sb.append("kaufen: ");
                    }

                    if (a.fourK() != null) {
                        sb.append("4k: ").append(a.fourK().value()).append(" ");
                    }
                    if (a.hd() != null) {
                        sb.append("HD: ").append(a.hd().value()).append(" ");
                    }
                    if (a.sd() != null) {
                        sb.append("SD: ").append(a.sd().value()).append(" ");
                    }
                    return sb.toString();
                }).collect(Collectors.joining(", "));
    }
}
