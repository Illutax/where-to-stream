package tech.dobler.where2stream.purchaseoffers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.UUID;

/**
 * How many calls one user has spent on one quota day.
 *
 * <p>Its own table rather than columns on {@code AppUser}: the user belongs to Account &amp; Access
 * and the quota budget to Purchase Offers, so a column there would put one context's state inside
 * another's entity (ADR-0014) and write to the user row on every price lookup.
 *
 * <p>Persisted for the same reason as the global counter: were these in memory only, a deploy would
 * hand every user a fresh daily allowance, and the global ceiling would become the only real
 * protection.
 *
 * <p>{@code userId} is a plain {@link UUID}, not a relation — Purchase Offers has no business
 * mapping to {@code AppUser}. The price is that rows are not cleaned up when a user is deleted;
 * ADR-0017 records that as an open point, to be solved from this side rather than by letting
 * Account &amp; Access depend on this context.
 */
@Entity
@Table(name = "ebay_user_quota_day",
        uniqueConstraints = @UniqueConstraint(name = "uk_ebay_user_quota_day", columnNames = {"quota_day", "user_id"}))
public class UserQuotaUsage {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "quota_day", nullable = false)
    private LocalDate quotaDay;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "calls_used", nullable = false)
    private long callsUsed;

    protected UserQuotaUsage() {
        // for JPA
    }

    public UserQuotaUsage(QuotaDay quotaDay, UUID userId) {
        this.id = UUID.randomUUID();
        this.quotaDay = quotaDay.startingOn();
        this.userId = userId;
        this.callsUsed = 0;
    }

    public QuotaDay quotaDay() {
        return new QuotaDay(quotaDay);
    }

    public UUID userId() {
        return userId;
    }

    public long callsUsed() {
        return callsUsed;
    }

    public void recordCalls(int calls) {
        this.callsUsed += calls;
    }
}
