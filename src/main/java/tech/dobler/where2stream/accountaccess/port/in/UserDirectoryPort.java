package tech.dobler.where2stream.accountaccess.port.in;

/**
 * Published facts about the user base that other bounded contexts need.
 *
 * <p>Currently just the head count, which Purchase Offers uses as the {@code n} in the daily quota
 * split {@code 10000 / n} (ADR-0017). It is published as a port rather than letting that context
 * reach for {@code AppUserRepository}, which the isolation rules in {@code ArchitectureTest}
 * forbid — and rightly so: a caller that only needs a number should not gain access to user rows.
 */
public interface UserDirectoryPort {

    /** How many users are registered right now. */
    long registeredUserCount();
}
