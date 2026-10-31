package com.abdulzafer.fx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Release metadata written by the pipeline as key=value lines in a RELEASE file. */
public record ReleaseInfo(String release, String commit, String built) {

    public static ReleaseInfo read(Path file) {
        String release = "dev";
        String commit = "unknown";
        String built = "unknown";
        if (file != null && Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    switch (key) {
                        case "release" -> release = value;
                        case "commit" -> commit = value;
                        case "built" -> built = value;
                        default -> { }
                    }
                }
            } catch (IOException e) {
                // Unreadable file: keep the defaults so the service still starts.
            }
        }
        return new ReleaseInfo(release, commit, built);
    }

    String toJson(String status) {
        return "{\"status\":" + Json.quote(status)
                + ",\"release\":" + Json.quote(release)
                + ",\"commit\":" + Json.quote(commit)
                + ",\"built\":" + Json.quote(built) + "}";
    }
}
