package tech.dobler.werstreamt.services;

import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class FileUtils {
    @Value("${wer-streamt.path}")
    private String filePath;

    public List<String> availableLists()
    {
        final var assetsPath = Paths.get(filePath);
        final var fileNames = Objects.requireNonNull(assetsPath.toFile().list(), () -> "Expect %s to contain at least one file".formatted(filePath));
        return Arrays.stream(fileNames)
                .sorted()
                .toList();
    }
}
