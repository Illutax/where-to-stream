package tech.dobler.where2stream.purchaseoffers.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.accountaccess.port.in.UserDirectoryPort;
import tech.dobler.where2stream.purchaseoffers.adapter.out.ebay.EbayProperties;
import tech.dobler.where2stream.purchaseoffers.domain.GlobalQuotaUsage;
import tech.dobler.where2stream.purchaseoffers.domain.QuotaDay;
import tech.dobler.where2stream.purchaseoffers.domain.QuotaVerdict;
import tech.dobler.where2stream.purchaseoffers.domain.UserQuotaUsage;
import tech.dobler.where2stream.purchaseoffers.port.out.GlobalQuotaUsageRepository;
import tech.dobler.where2stream.purchaseoffers.port.out.UserQuotaUsageRepository;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.util.UUID;

/**
 * Rations the shared daily eBay call budget between users (ADR-0017).
 *
 * <p>Two counters, both per quota day:
 * <ul>
 *   <li>a <strong>per-user</strong> allowance of {@code dailyCallBudget * overbooking / n}, which
 *       is deliberately overbooked — at the configured factor of 2 the budget is handed out twice
 *       over, on the assumption that at most half the registered users fetch prices on a given
 *       day;</li>
 *   <li>a <strong>global</strong> ceiling at the full {@code dailyCallBudget}, which is what
 *       actually protects the allowance once that assumption fails.</li>
 * </ul>
 *
 * <p>The per-user limit is not primarily a fairness device: at today's five users it works out to
 * far more calls than any watchlist can spend. What it does prevent is one account — or a script
 * holding a valid session cookie — draining the shared budget on its own.
 *
 * <p><strong>eBay's answer outranks our counter.</strong> {@link #recordExhaustedByUpstream} closes
 * the day immediately and durably, whatever our own arithmetic says. Our count drifts through
 * retries, concurrent calls and restarts; eBay's does not.
 */
@Slf4j
@Service
public class QuotaService {

    private final EbayProperties properties;
    private final GlobalQuotaUsageRepository globalUsage;
    private final UserQuotaUsageRepository userUsage;
    private final UserDirectoryPort userDirectory;
    private final TimeService timeService;

    public QuotaService(EbayProperties properties, GlobalQuotaUsageRepository globalUsage,
                        UserQuotaUsageRepository userUsage, UserDirectoryPort userDirectory,
                        TimeService timeService) {
        this.properties = properties;
        this.globalUsage = globalUsage;
        this.userUsage = userUsage;
        this.userDirectory = userDirectory;
        this.timeService = timeService;
    }

    /** The quota period we are currently in, in eBay's reset zone rather than ours. */
    public QuotaDay currentQuotaDay() {
        return QuotaDay.at(timeService.now(), properties.quota().resetZone(), properties.quota().resetTime());
    }

    /**
     * Books {@code calls} against both counters if both allow it.
     *
     * <p>Reserves up front rather than counting afterwards: a call that fails in flight may still
     * have reached eBay, so counting only successes would systematically under-count and overshoot
     * the allowance.
     */
    @Transactional
    public QuotaVerdict tryReserve(UUID userId, int calls) {
        final var day = currentQuotaDay();
        final var global = loadOrCreateGlobal(day);

        if (global.isExhausted()) {
            log.info("eBay lookup refused: the shared budget for {} was reported exhausted at {}",
                    day, global.exhaustedAt());
            return QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED;
        }
        if (global.callsUsed() + calls > properties.quota().dailyCallBudget()) {
            log.warn("eBay lookup refused: the shared budget for {} is used up ({}/{} calls) — "
                            + "the overbooking assumption did not hold today",
                    day, global.callsUsed(), properties.quota().dailyCallBudget());
            return QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED;
        }

        final var perUser = perUserAllowance();
        final var user = userUsage.findByQuotaDayAndUserId(day.startingOn(), userId)
                .orElseGet(() -> userUsage.save(new UserQuotaUsage(day, userId)));
        if (user.callsUsed() + calls > perUser) {
            log.info("eBay lookup refused for user {}: personal allowance for {} used up ({}/{} calls)",
                    userId, day, user.callsUsed(), perUser);
            return QuotaVerdict.USER_ALLOWANCE_REACHED;
        }

        // No save(): both entities are managed inside this transaction, so Hibernate's dirty
        // checking flushes the new counts at commit. The only save() calls are the ones that turn a
        // brand-new, transient row into a managed one — dirty checking has nothing to track before
        // that.
        global.recordCalls(calls);
        user.recordCalls(calls);
        return QuotaVerdict.ALLOWED;
    }

    /**
     * Closes the current quota day because eBay reported the allowance spent.
     *
     * <p>Written to the database rather than held in memory: a restart would otherwise lift the
     * lockout and the application would resume calling an allowance that is gone.
     */
    @Transactional
    public void recordExhaustedByUpstream(String upstreamDetail) {
        final var day = currentQuotaDay();
        final var global = loadOrCreateGlobal(day);
        global.markExhausted(timeService.now());
        // Logged in full because this is the only evidence we have for when eBay's day rolls over —
        // the reset time in configuration is an unverified hypothesis (ADR-0017).
        log.warn("eBay reported the daily quota exhausted for {} (our count: {} calls). Upstream said: {}",
                day, global.callsUsed(), upstreamDetail);
    }

    /**
     * Loads the day's shared counter, inserting it on first use.
     *
     * <p>The {@code save} on the miss path is not redundant with dirty checking: a freshly
     * constructed entity is transient, and Hibernate only tracks changes to entities it manages.
     * A loaded one needs no save at all.
     *
     * <p>Both entities carry assigned identifiers rather than generated ones, so Spring Data's
     * {@code save} takes the {@code merge} branch and costs one extra SELECT on insert. That is
     * once per quota day (and once per user per quota day), which is not worth optimising away.
     */
    private GlobalQuotaUsage loadOrCreateGlobal(QuotaDay day) {
        return globalUsage.findById(day.startingOn())
                .orElseGet(() -> globalUsage.save(new GlobalQuotaUsage(day)));
    }

    /**
     * {@code dailyCallBudget * overbooking / n}, at least one call so a user is never locked out
     * entirely by rounding.
     */
    long perUserAllowance() {
        final var users = Math.max(1, userDirectory.registeredUserCount());
        final var quota = properties.quota();
        return Math.max(1, (long) quota.dailyCallBudget() * quota.perUserOverbooking() / users);
    }
}
