package tech.dobler.werstreamt.services;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileUtils {
    public static List<String> availableLists()
    {
        final var assetsPath = Paths.get("assets");
        final var fileNames = Objects.requireNonNull(assetsPath.toFile().list());
        return Arrays.stream(fileNames)
                .sorted()
                .toList();
    }
}
