package tech.dobler.where2stream.accountaccess.port.in;

/**
 * How many accounts this instance has, published so the platform's metrics view can ask without
 * depending on {@code AppUserRepository}.
 *
 * <p>Separate from {@link CurrentUserPort} on purpose: that one answers "who is this request",
 * a per-request question every context asks. This one is an operator statistic with exactly one
 * caller, and folding it in would make every consumer of {@code CurrentUserPort} depend on a
 * capability none of them wants.
 */
public interface UserMetricsPort {

    long countUsers();
}
