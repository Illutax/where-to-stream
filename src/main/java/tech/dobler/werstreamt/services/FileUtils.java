package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tech.dobler.werstreamt.configurations.WerStreamtProperties;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public final class FileUtils {
    private final WerStreamtProperties properties;

    public List<String> availableLists()
    {
        final var path = properties.path();
        final var assetsPath = Paths.get(path);
        final var fileNames = Objects.requireNonNull(assetsPath.toFile().list(), () -> "Expect %s to contain at least one file".formatted(path));
        return Arrays.stream(fileNames)
                .sorted()
                .toList();
    }
}
