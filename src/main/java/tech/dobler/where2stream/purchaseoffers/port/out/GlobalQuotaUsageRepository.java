package tech.dobler.where2stream.purchaseoffers.port.out;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.purchaseoffers.domain.GlobalQuotaUsage;

import java.time.LocalDate;

/** The quota day's shared counter; the repository interface is the outbound port (ADR-0014). */
@Repository
public interface GlobalQuotaUsageRepository extends ListCrudRepository<GlobalQuotaUsage, LocalDate> {
}
