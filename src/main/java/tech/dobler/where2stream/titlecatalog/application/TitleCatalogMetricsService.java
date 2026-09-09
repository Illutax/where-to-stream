package tech.dobler.where2stream.titlecatalog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.titlecatalog.port.in.TitleCatalogMetricsPort;
import tech.dobler.where2stream.titlecatalog.port.out.TitleMetaRepository;
import tech.dobler.where2stream.titlecatalog.port.out.TitlePosterRepository;

/** Title Catalog's side of the instance metrics: cache sizes, and how much of each is a negative. */
@Service
@RequiredArgsConstructor
public class TitleCatalogMetricsService implements TitleCatalogMetricsPort {

    private final TitleMetaRepository titleMetaRepository;
    private final TitlePosterRepository titlePosterRepository;

    @Override
    public TitleCatalogMetrics metrics() {
        return new TitleCatalogMetrics(
                titleMetaRepository.count(),
                titleMetaRepository.countWithoutData(),
                titlePosterRepository.count(),
                titlePosterRepository.countByPosterPathIsNull());
    }
}
