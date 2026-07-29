package tech.dobler.where2stream.shared.time;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

/** Production {@link TimeService} backed by the real system clock. */
@Service
public class SystemTimeService implements TimeService {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDate today() {
        return LocalDate.now();
    }
}
