package tech.dobler.where2stream.accountaccess.port.spi;

/**
 * Whether the SPA must show the TMDB attribution footer — supplied to Account &amp; Access by
 * whichever context owns the poster source.
 *
 * <p><strong>This interface is declared here and implemented elsewhere.</strong> That inversion is
 * the whole point. {@code /api/me} needs the flag for {@code MeDto}, but Account &amp; Access has no
 * business knowing what a poster source is, let alone which one is active. So it states its need as
 * an interface of its own and lets the context that has the answer satisfy it — today
 * {@code TmdbProperties} in Title Catalog.
 *
 * <p>The alternative that was tried first and rejected: leaving the interface in
 * {@code titlecatalog.port.in} and having Account &amp; Access import it. That made Account &amp;
 * Access depend on Title Catalog while Title Catalog already depended back on it through
 * {@code CurrentUserPort} — a cycle between two bounded contexts that the per-context isolation
 * rules could not see, since each of them checks a single direction. Moving the interface into
 * {@code shared} would have made the cycle disappear from the rules without removing the coupling;
 * this removes it, because the arrow now points one way only.
 *
 * <p><strong>Why {@code port.spi} and not {@code port.out}.</strong> An outbound port is this
 * context's own dependency on its database or on an external system, and nothing outside may touch
 * it. This is the other kind: a service-provider interface that another bounded context is invited
 * to implement. Different intent, different package, and the architecture rules treat them
 * differently — {@code port.spi} is published, {@code port.out} stays private.
 */
public interface PosterAttributionProvider {

    boolean tmdbAttributionRequired();
}
