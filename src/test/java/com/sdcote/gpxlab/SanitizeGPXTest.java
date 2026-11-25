package com.sdcote.gpxlab;

import com.sdcote.gpxlab.sanitize.MetadataSanitizer;
import com.sdcote.gpxlab.sanitize.TrackTimeSanitizer;
import com.sdcote.gpxlab.ui.SanitizeParametersDialog;
import coyote.commons.cfg.Config;
import coyote.commons.cfg.ConfigUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class SanitizeGPXTest {
    private static final String GPX = """
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="private">
              <metadata><name>Secret</name></metadata>
              <trk><trkseg><trkpt lat="1" lon="2"><time>2020-01-01T00:00:00Z</time></trkpt></trkseg></trk>
            </gpx>
            """;

    @Test
    void selectedSanitizersWriteSeparateFile(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("input.gpx");
        Files.writeString(input, GPX);
        List<String> messages = new ArrayList<>();
        Path output = SanitizeGPX.execute(input, List.of(new MetadataSanitizer(), new TrackTimeSanitizer()), messages::add);
        String result = Files.readString(output);
        assertEquals(directory.resolve("input-sanitized.gpx"), output);
        assertEquals(GPX, Files.readString(input));
        assertFalse(result.contains("creator="));
        assertFalse(result.contains("<metadata>"));
        assertFalse(result.contains("<time>"));
        assertTrue(result.contains("trkpt"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Finished")));
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> SanitizeGPX.execute(input, List.of(new TrackTimeSanitizer()), messages::add));
        assertEquals(result, Files.readString(output));
    }

    @Test
    void onlySelectedSanitizerRuns(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("track.GPX");
        Files.writeString(input, GPX);
        Path output = SanitizeGPX.execute(input, List.of(new TrackTimeSanitizer()), message -> { });
        String result = Files.readString(output);
        assertTrue(result.contains("creator=\"private\""));
        assertTrue(result.contains("Secret"));
        assertFalse(result.contains("<time>"));
    }

    @Test
    void rejectsEmptySelectionAndInvalidInput(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("invalid.gpx");
        Files.writeString(input, "not GPX");
        assertThrows(IllegalArgumentException.class, () -> SanitizeGPX.execute(input, List.of(), message -> { }));
        assertThrows(Exception.class, () -> SanitizeGPX.execute(input, List.of(new MetadataSanitizer()), message -> { }));
        assertFalse(Files.exists(directory.resolve("invalid-sanitized.gpx")));
    }

    @Test
    void explicitOutputRequiresOverwriteApproval(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("input.gpx");
        Path output = directory.resolve("custom.gpx");
        Files.writeString(input, GPX);
        Files.writeString(output, "existing");
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> SanitizeGPX.execute(input, output, false, List.of(new TrackTimeSanitizer()), message -> { }));
        assertEquals("existing", Files.readString(output));
        assertEquals(output, SanitizeGPX.execute(input, output, true, List.of(new TrackTimeSanitizer()), message -> { }));
        assertFalse(Files.readString(output).contains("<time>"));
        assertEquals(GPX, Files.readString(input));
    }

    @Test
    void explicitOutputCanReplaceInputAfterReading(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("input.gpx");
        Files.writeString(input, GPX);
        SanitizeGPX.execute(input, input, true, List.of(new TrackTimeSanitizer()), message -> { });
        assertFalse(Files.readString(input).contains("<time>"));
    }

    @Test
    void cancellationBeforeWritingPreservesExistingOutput(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("input.gpx");
        Path output = directory.resolve("output.gpx");
        Files.writeString(input, GPX);
        Files.writeString(output, "existing");
        assertThrows(CancellationException.class, () -> SanitizeGPX.execute(input, output, true,
                List.of(new TrackTimeSanitizer()), message -> {
                    if (message.startsWith("Writing ")) throw new CancellationException();
                }));
        assertEquals("existing", Files.readString(output));
    }

    @Test
    void settingsRoundTripThroughMasterConfiguration(@TempDir Path directory) throws Exception {
        Config config = new Config();
        config.set("unrelated", "keep");
        assertNull(SanitizeGPX.loadSettings(config).indexes());
        Path input = directory.resolve("input.gpx");
        Path output = directory.resolve("custom.gpx");
        SanitizeGPX.saveSettings(config, new SanitizeParametersDialog.Parameters(input, output, List.of(1)));
        Path file = directory.resolve("config.json");
        ConfigUtil.writeConfig(config, file.toString());
        Config restored = ConfigUtil.readConfig(file.toString());
        var settings = SanitizeGPX.loadSettings(restored);
        assertEquals(input, settings.input());
        assertEquals(output, settings.output());
        assertEquals(List.of(1), settings.indexes());
        assertEquals("keep", restored.getString("unrelated"));
        SanitizeGPX.saveSettings(restored, new SanitizeParametersDialog.Parameters(input, output, List.of(0)));
        assertEquals(List.of(0), SanitizeGPX.loadSettings(restored).indexes());
    }
}