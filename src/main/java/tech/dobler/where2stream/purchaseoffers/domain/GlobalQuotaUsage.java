package tech.dobler.where2stream.purchaseoffers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * How much of the shared daily call budget a quota day has consumed, and whether eBay has already
 * declared it spent.
 *
 * <p>Persisted — unlike the prices themselves, which are deliberately not (plan, section 5.6).
 * The reason is the exhaustion flag: if it lived only in memory, an evening deploy would clear the
 * day's lockout and the application would start calling an allowance eBay considers gone. The
 * running counter is persisted alongside it because it would otherwise reset with every restart
 * and systematically under-count.
 */
@Entity
@Table(name = "ebay_quota_day")
public class GlobalQuotaUsage {

    @Id
    @Column(name = "quota_day", nullable = false)
    private LocalDate quotaDay;

    @Column(name = "calls_used", nullable = false)
    private long callsUsed;

    /** Set when eBay itself reported the allowance spent — see {@link #isExhausted()}. */
    @Column(name = "exhausted_at")
    private Instant exhaustedAt;

    protected GlobalQuotaUsage() {
        // for JPA
    }

    public GlobalQuotaUsage(QuotaDay quotaDay) {
        this.quotaDay = quotaDay.startingOn();
        this.callsUsed = 0;
    }

    public QuotaDay quotaDay() {
        return new QuotaDay(quotaDay);
    }

    public long callsUsed() {
        return callsUsed;
    }

    public void recordCalls(int calls) {
        this.callsUsed += calls;
    }

    /**
     * Whether the day is over as far as calling eBay goes.
     *
     * <p>Deliberately <em>not</em> derived from {@link #callsUsed} against the budget: our counter
     * is an estimate, eBay's answer is the fact. The application layer checks both, but only this
     * flag survives being wrong in our favour.
     */
    public boolean isExhausted() {
        return exhaustedAt != null;
    }

    public Instant exhaustedAt() {
        return exhaustedAt;
    }

    /** Idempotent: the first report wins, so the recorded moment is when we first learned of it. */
    public void markExhausted(Instant at) {
        if (exhaustedAt == null) {
            this.exhaustedAt = at;
        }
    }
}
