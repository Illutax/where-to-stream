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
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .referrer("https://www.google.com/");
    }
}
