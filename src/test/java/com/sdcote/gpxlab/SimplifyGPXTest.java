package com.sdcote.gpxlab;

import coyote.commons.cfg.Config;
import coyote.commons.cfg.ConfigUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class SimplifyGPXTest {
    private static final String GPX = """
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="test">
              <trk><trkseg>
                <trkpt lat="1.0000001" lon="2"><ele>10</ele><time>2020-01-01T00:00:00Z</time></trkpt>
                <trkpt lat="1.0000101" lon="2"><ele>20</ele></trkpt>
                <trkpt lat="1.0000201" lon="2"><ele>30</ele></trkpt>
              </trkseg></trk>
            </gpx>
            """;

    /** Checks that the delegated algorithm produces exactly the same serialized output. */
    @Test
    void matchesOriginalAlgorithmAndUsesDefaultOutput(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("track.GPX");
        Path expected = directory.resolve("expected.gpx");
        Files.writeString(input, GPX);
        var config = new GpxSimplifier.SimplificationConfig(0, 0, 15);
        GpxSimplifier.simplifyGpx(input, expected, config);
        var messages = new ArrayList<String>();
        Path actual = SimplifyGPX.execute(input, config, messages::add);
        assertEquals(directory.resolve("track-simplified.gpx"), actual);
        assertEquals(Files.readString(expected), Files.readString(actual));
        assertEquals(GPX, Files.readString(input));
        assertEquals("Finished", messages.getLast());
        assertThrows(FileAlreadyExistsException.class, () -> SimplifyGPX.execute(input, config, messages::add));
    }

    /** Checks overwrite approval, including an in-place replacement. */
    @Test
    void overwriteRequiresApproval(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("input.gpx");
        Path output = directory.resolve("output.gpx");
        Files.writeString(input, GPX);
        Files.writeString(output, "original");
        var config = GpxSimplifier.SimplificationConfig.defaults();
        assertThrows(FileAlreadyExistsException.class, () -> SimplifyGPX.execute(input, output, false, config, s -> { }));
        assertEquals("original", Files.readString(output));
        SimplifyGPX.execute(input, output, true, config, s -> { });
        assertNotEquals("original", Files.readString(output));
        SimplifyGPX.execute(input, input, true, config, s -> { });
        assertEquals(Files.readString(output), Files.readString(input));
    }

    /** Checks that cancellation and malformed input leave the destination intact. */
    @Test
    void failureAndCancellationPreserveDestination(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("input.gpx");
        Path output = directory.resolve("output.gpx");
        Files.writeString(input, GPX);
        Files.writeString(output, "original");
        var config = GpxSimplifier.SimplificationConfig.defaults();
        assertThrows(CancellationException.class, () -> SimplifyGPX.execute(input, output, true, config, s -> {
            if (s.startsWith("Writing ")) throw new CancellationException();
        }));
        assertEquals("original", Files.readString(output));
        Files.writeString(input, "invalid");
        assertThrows(Exception.class, () -> SimplifyGPX.execute(input, output, true, config, s -> { }));
        assertEquals("original", Files.readString(output));
        try (var files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }

    /** Checks settings round-trip through the same configuration serialization as the application. */
    @Test
    void settingsRoundTrip(@TempDir Path directory) throws Exception {
        Config config = new Config();
        config.set("unrelated", "keep");
        assertEquals(GpxSimplifier.SimplificationConfig.defaults(), SimplifyGPX.loadSettings(null).config());
        var settings = new SimplifyGPX.Settings(directory.resolve("in.gpx"), directory.resolve("out.gpx"),
                new GpxSimplifier.SimplificationConfig(-1, 3.5, 0));
        SimplifyGPX.saveSettings(config, settings);
        Path file = directory.resolve("config.json");
        ConfigUtil.writeConfig(config, file.toString());
        Config restored = ConfigUtil.readConfig(file.toString());
        assertEquals(settings, SimplifyGPX.loadSettings(restored));
        assertEquals("keep", restored.getString("unrelated"));
        SimplifyGPX.saveSettings(restored, new SimplifyGPX.Settings(null, null, settings.config()));
        assertNull(SimplifyGPX.loadSettings(restored).input());
        assertNull(SimplifyGPX.loadSettings(restored).output());
    }
}