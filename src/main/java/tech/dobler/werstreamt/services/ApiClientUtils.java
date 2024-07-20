package tech.dobler.werstreamt.services;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.web.util.UriComponents;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiClientUtils {
    public static Connection getConnectionWithUserAgent(UriComponents query) {
        return Jsoup.connect(query.toString())
                .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                .referrer("http://www.google.com");
    }
}
