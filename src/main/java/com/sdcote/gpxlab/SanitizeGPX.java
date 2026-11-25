package com.sdcote.gpxlab;

import com.sdcote.gpxlab.sanitize.ISanitizer;
import com.sdcote.gpxlab.sanitize.MetadataSanitizer;
import com.sdcote.gpxlab.sanitize.TrackTimeSanitizer;
import com.sdcote.gpxlab.ui.SanitizeParametersDialog;
import coyote.commons.cfg.Config;
import coyote.commons.dataframe.DataFrame;
import io.jenetics.jpx.GPX;
import javafx.application.Platform;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** Runs selected GPX sanitizers and writes a separate output file. */
public final class SanitizeGPX {
    private static final String CONFIG_SECTION = "SanitizeGPX";
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String SANITIZER_PREFIX = "sanitizer.";
    private static final List<Choice> SANITIZERS = List.of(
            new Choice("Remove metadata", new MetadataSanitizer()),
            new Choice("Remove track point times", new TrackTimeSanitizer()));

    private record Choice(String name, ISanitizer sanitizer) { }

    /** Previously saved values, or null fields when no usable values exist. */
    record Settings(Path input, Path output, List<Integer> indexes) { }

    /** Prevents construction. */
    private SanitizeGPX() { }

    /** Opens the parameters dialog and starts processing when Execute is selected on the JavaFX thread. */
    public static void display() {
        Settings settings = loadSettings(GPXLab.CONFIG);
        var dialog = new SanitizeParametersDialog(null, SANITIZERS.stream().map(Choice::name).toList(),
                settings.input(), settings.output(), settings.indexes());
        dialog.showAndWait().ifPresent(parameters -> {
            saveSettings(GPXLab.CONFIG, parameters);
            GPXLab.saveConfig();
            var status = new StatusDialog("Sanitize GPX Status");
            status.setProgress(-1);
            status.show();
            List<ISanitizer> selected = parameters.sanitizerIndexes().stream()
                    .map(index -> SANITIZERS.get(index).sanitizer()).toList();
            Thread worker = new Thread(() -> {
                try {
                    Path output = execute(parameters.path(), parameters.outputPath(), parameters.overwriteApproved(), selected,
                            message -> {
                                if (!message.equals("Finished") && status.isCanceled()) throw new CancellationException();
                                status.setStatusMessage(message);
                            });
                    status.setProgress(1);
                    status.complete("Completed: " + output);
                } catch (CancellationException canceled) {
                    status.setProgress(0);
                    status.complete("Sanitization canceled");
                } catch (Exception error) {
                    status.setProgress(0);
                    status.fail("Sanitization failed");
                    Platform.runLater(() -> ErrorDialog.show(status, "Sanitize GPX Error",
                            "Could not sanitize the GPX file", error));
                }
            }, "gpx-sanitizer");
            worker.setDaemon(true);
            worker.start();
        });
    }

    /**
     * Reads previously executed task settings without changing the master configuration.
     *
     * @param config master configuration, or null
     * @return saved settings, with null selection for the default selection
     */
    static Settings loadSettings(Config config) {
        if (config == null || !(config.get(CONFIG_SECTION) instanceof DataFrame frame)) {
            return new Settings(null, null, null);
        }
        Config section = new Config(frame);
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < SANITIZERS.size(); i++) {
            String value = section.getString(SANITIZER_PREFIX + SANITIZERS.get(i).sanitizer().getClass().getName());
            if (Boolean.parseBoolean(value)) indexes.add(i);
        }
        return new Settings(readPath(section.getString(INPUT)), readPath(section.getString(OUTPUT)), List.copyOf(indexes));
    }

    /**
     * Converts a stored path safely, ignoring absent or invalid values.
     *
     * @param value stored path text
     * @return path or null when the value is absent or invalid
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
     * Saves the executed parameters into the master configuration, preserving other sections.
     *
     * @param config master configuration
     * @param parameters parameters confirmed for execution
     */
    static void saveSettings(Config config, SanitizeParametersDialog.Parameters parameters) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(parameters, "parameters");
        Config section = config.get(CONFIG_SECTION) instanceof DataFrame frame ? new Config(frame) : new Config();
        section.removeAll(INPUT);
        section.removeAll(OUTPUT);
        section.put(INPUT, parameters.path().toString());
        section.put(OUTPUT, parameters.outputPath().toString());
        for (int i = 0; i < SANITIZERS.size(); i++) {
            String key = SANITIZER_PREFIX + SANITIZERS.get(i).sanitizer().getClass().getName();
            section.removeAll(key);
            section.put(key, parameters.sanitizerIndexes().contains(i));
        }
        config.removeAll(CONFIG_SECTION);
        config.put(CONFIG_SECTION, section);
    }

    /**
     * Reads and sanitizes a file, writing a new sibling file without overwriting existing files.
     *
     * @param input source GPX file
     * @param sanitizers transformations to apply in order
     * @param status receives progress messages
     * @return the path of the new file
     * @throws IOException if reading or writing fails
     * @throws ParserConfigurationException if a document cannot be created
     * @throws TransformerException if XML serialization fails
     * @throws IllegalArgumentException if no sanitizer is selected
     */
    public static Path execute(Path input, List<ISanitizer> sanitizers, Consumer<String> status)
            throws IOException, ParserConfigurationException, TransformerException {
        Objects.requireNonNull(input, "input");
        String filename = input.getFileName().toString();
        int extension = filename.toLowerCase(java.util.Locale.ROOT).endsWith(".gpx") ? filename.length() - 4 : filename.length();
        return execute(input, input.resolveSibling(filename.substring(0, extension) + "-sanitized.gpx"), false, sanitizers, status);
    }

    /**
     * Reads and sanitizes a file, writing to the specified output after serialization.
     *
     * @param input source GPX file
     * @param output destination file
     * @param overwrite whether replacement of an existing destination is authorized
     * @param sanitizers transformations to apply in order
     * @param status receives progress messages
     * @return the destination path
     * @throws IOException if reading or writing fails, or the destination already exists without approval
     * @throws ParserConfigurationException if a document cannot be created
     * @throws TransformerException if XML serialization fails
     * @throws IllegalArgumentException if no sanitizer is selected
     */
    public static Path execute(Path input, Path output, boolean overwrite, List<ISanitizer> sanitizers, Consumer<String> status)
            throws IOException, ParserConfigurationException, TransformerException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        List<ISanitizer> selected = List.copyOf(sanitizers);
        Objects.requireNonNull(status, "status");
        if (selected.isEmpty()) throw new IllegalArgumentException("Select at least one sanitizer");
        status.accept("Reading " + input);
        GPX gpx = GPX.read(input);
        for (ISanitizer sanitizer : selected) {
            status.accept("Applying " + sanitizer.getClass().getSimpleName());
            gpx = sanitizer.sanitize(gpx);
        }
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        GPX.Writer.of(GPX.Writer.Indent.NULL).write(gpx, new DOMResult(document));
        for (ISanitizer sanitizer : selected) sanitizer.postProcess(document);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(bytes));
        status.accept("Writing " + output);
        Files.write(output, bytes.toByteArray(), overwrite ? StandardOpenOption.CREATE : StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, overwrite ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.WRITE);
        status.accept("Finished");
        return output;
    }
}