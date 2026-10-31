package com.abdulzafer.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseInfoTest {

    @Test
    void readsPipelineReleaseFile() throws IOException {
        Path file = Files.createTempFile("release", ".txt");
        try {
            Files.writeString(file,
                    "release=00012-9c1d2e3\ncommit=9c1d2e3abc\nbuilt=2026-01-01T00:00:00Z\nextra=ignored\n",
                    StandardCharsets.UTF_8);
            ReleaseInfo info = ReleaseInfo.read(file);
            assertEquals("00012-9c1d2e3", info.release());
            assertEquals("9c1d2e3abc", info.commit());
            assertEquals("2026-01-01T00:00:00Z", info.built());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void defaultsWhenFileMissing() {
        ReleaseInfo info = ReleaseInfo.read(Path.of("does-not-exist-" + System.nanoTime()));
        assertEquals("dev", info.release());
        assertEquals("unknown", info.commit());
    }

    @Test
    void healthJsonHasExpectedShape() {
        ReleaseInfo info = new ReleaseInfo("00001-abc1234", "abc1234", "2026-01-01T00:00:00Z");
        assertEquals("{\"status\":\"ok\",\"release\":\"00001-abc1234\",\"commit\":\"abc1234\","
                + "\"built\":\"2026-01-01T00:00:00Z\"}", info.toJson("ok"));
    }
}
