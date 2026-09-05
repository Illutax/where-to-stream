package tech.dobler.where2stream.purchaseoffers.port.out;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.purchaseoffers.domain.UserQuotaUsage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Per-user counters for a quota day; the repository interface is the outbound port (ADR-0014). */
@Repository
public interface UserQuotaUsageRepository extends ListCrudRepository<UserQuotaUsage, UUID> {

    Optional<UserQuotaUsage> findByQuotaDayAndUserId(LocalDate quotaDay, UUID userId);

    /** Retained deliberately (ADR-0017): the history is what shows how often the split's assumption breaks. */
    List<UserQuotaUsage> findByQuotaDay(LocalDate quotaDay);
}
