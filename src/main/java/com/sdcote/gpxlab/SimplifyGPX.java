package com.sdcote.gpxlab;

import coyote.commons.cfg.Config;
import coyote.commons.dataframe.DataFrame;
import com.sdcote.gpxlab.ui.SimplifyParametersDialog;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** Reusable file processing and settings for the GPX simplifier. */
public final class SimplifyGPX {
    private static final String SECTION = "SimplifyGPX";
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String MIN_STEP = "minStepMeters";
    private static final String EPSILON = "rdpEpsilonMeters";
    private static final String WINDOW = "elevationWindowMeters";

    /**
     * Saved paths and algorithm parameters. Missing paths are represented by null.
     *
     * @param input input path, or null
     * @param output output path, or null
     * @param config simplification parameters
     */
    public record Settings(Path input, Path output, GpxSimplifier.SimplificationConfig config) { }

    /** Prevents construction. */
    private SimplifyGPX() { }

    /** Opens the parameters dialog and starts processing on confirmation, on the JavaFX thread. */
    public static void display() {
        Settings settings = loadSettings(GPXLab.CONFIG);
        var dialog = new SimplifyParametersDialog(null, settings.input(), settings.output(), settings.config());
        dialog.showAndWait().ifPresent(parameters -> {
            saveSettings(GPXLab.CONFIG, new Settings(parameters.path(), parameters.outputPath(), parameters.config()));
            GPXLab.saveConfig();
            var status = new StatusDialog("GPX Simplifier Status");
            status.setProgress(-1);
            status.show();
            Thread worker = new Thread(() -> {
                try {
                    Path output = execute(parameters.path(), parameters.outputPath(),
                            parameters.overwriteApproved(), parameters.config(), message -> {
                                if (!message.equals("Finished") && status.isCanceled()) throw new CancellationException();
                                status.setStatusMessage(message);
                            });
                    status.setProgress(1);
                    status.complete("Completed: " + output);
                } catch (CancellationException canceled) {
                    status.setProgress(0);
                    status.complete("Simplification canceled");
                } catch (Exception error) {
                    status.setProgress(0);
                    status.fail("Simplification failed");
                    Platform.runLater(() -> ErrorDialog.show(status, "GPX Simplifier Error",
                            "Could not simplify the GPX file", error));
                }
            }, "gpx-simplifier");
            worker.setDaemon(true);
            worker.start();
        });
    }

    /**
     * Loads settings without modifying the master configuration. Missing or invalid values use defaults.
     *
     * @param config master configuration, or null
     * @return saved settings
     */
    public static Settings loadSettings(Config config) {
        var defaults = GpxSimplifier.SimplificationConfig.defaults();
        if (config == null || !(config.get(SECTION) instanceof DataFrame frame)) {
            return new Settings(null, null, defaults);
        }
        Config section = new Config(frame);
        return new Settings(readPath(section.getString(INPUT)), readPath(section.getString(OUTPUT)),
                new GpxSimplifier.SimplificationConfig(readDouble(section.getString(MIN_STEP), defaults.minStepMeters()),
                        readDouble(section.getString(EPSILON), defaults.rdpEpsilonMeters()),
                        readDouble(section.getString(WINDOW), defaults.elevationWindowMeters())));
    }

    /**
     * Saves settings in the master configuration; the caller is responsible for persisting it.
     *
     * @param config master configuration
     * @param settings settings to save
     */
    public static void saveSettings(Config config, Settings settings) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(settings.config(), "settings.config");
        Config section = config.get(SECTION) instanceof DataFrame frame ? new Config(frame) : new Config();
        section.removeAll(INPUT);
        section.removeAll(OUTPUT);
        section.removeAll(MIN_STEP);
        section.removeAll(EPSILON);
        section.removeAll(WINDOW);
        if (settings.input() != null) section.put(INPUT, settings.input().toString());
        if (settings.output() != null) section.put(OUTPUT, settings.output().toString());
        section.put(MIN_STEP, Double.toString(settings.config().minStepMeters()));
        section.put(EPSILON, Double.toString(settings.config().rdpEpsilonMeters()));
        section.put(WINDOW, Double.toString(settings.config().elevationWindowMeters()));
        config.removeAll(SECTION);
        config.put(SECTION, section);
    }

    /**
     * Simplifies to a sibling named with the {@code -simplified.gpx} suffix without overwriting.
     *
     * @param input source GPX file
     * @param config algorithm parameters
     * @param status status callback, which may throw a cancellation exception
     * @return output path
     * @throws IOException if reading or writing fails
     */
    public static Path execute(Path input, GpxSimplifier.SimplificationConfig config, Consumer<String> status) throws IOException {
        Objects.requireNonNull(input, "input");
        String filename = input.getFileName().toString();
        int extension = filename.toLowerCase(Locale.ROOT).endsWith(".gpx") ? filename.length() - 4 : filename.length();
        return execute(input, input.resolveSibling(filename.substring(0, extension) + "-simplified.gpx"), false, config, status);
    }

    /**
     * Runs the existing algorithm into a temporary file before publishing the result.
     * Cancellation can be requested by throwing from the status callback before publication;
     * the algorithm itself does not support interruption. The Finished notification is after publication.
     *
     * @param input source GPX file
     * @param output destination, which may be the input when overwrite is approved
     * @param overwrite whether replacement of an existing destination is authorized
     * @param config algorithm parameters, passed through unchanged
     * @param status status callback
     * @return destination path
     * @throws IOException if reading or writing fails, or an existing destination is not approved
     */
    public static Path execute(Path input, Path output, boolean overwrite, GpxSimplifier.SimplificationConfig config,
                               Consumer<String> status) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(status, "status");
        status.accept("Simplifying " + input);
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), ".gpx-simplify-", ".gpx");
        try {
            GpxSimplifier.simplifyGpx(input, temporary, config);
            status.accept("Writing " + output);
            if (overwrite) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(temporary, output);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        status.accept("Finished");
        return output;
    }

    /**
     * Converts stored path text to a path, ignoring invalid values.
     *
     * @param value stored path text
     * @return the path, or null if absent or invalid
     */
    private static Path readPath(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value);
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    /**
     * Converts stored numeric text, falling back when it is absent or invalid.
     *
     * @param value stored numeric text
     * @param fallback value to use when parsing fails
     * @return the parsed number or fallback
     */
    private static double readDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}