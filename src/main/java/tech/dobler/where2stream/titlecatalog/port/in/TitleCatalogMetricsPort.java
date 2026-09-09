package tech.dobler.where2stream.titlecatalog.port.in;

/** Title-cache sizes and coverage, published for the platform's metrics view. */
public interface TitleCatalogMetricsPort {

    /**
     * @param metadataRows        cached IMDb metadata rows
     * @param metadataWithoutData how many of those record "IMDb had nothing"
     * @param posterRows          cached poster rows
     * @param postersWithoutImage how many of those record "this title has no poster"
     */
    record TitleCatalogMetrics(long metadataRows, long metadataWithoutData,
                               long posterRows, long postersWithoutImage) {

        /** Rows that actually carry a poster — the number "posters cached" is usually meant to be. */
        public long postersWithImage() {
            return posterRows - postersWithoutImage;
        }
    }

    TitleCatalogMetrics metrics();
}
