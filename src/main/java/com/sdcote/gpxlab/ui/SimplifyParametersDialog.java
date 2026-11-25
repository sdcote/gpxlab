package com.sdcote.gpxlab.ui;

import com.sdcote.gpxlab.GpxSimplifier.SimplificationConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Dialog for selecting GPX simplification parameters. Construct and show on the JavaFX thread. */
public final class SimplifyParametersDialog extends Dialog<SimplifyParametersDialog.Parameters> {
    private static final String LABEL_STYLE = "-fx-text-fill: #ffffff;";
    private static final String BUTTON_STYLE = "-fx-background-color: #3c3f41; -fx-text-fill: #ffffff;";

    /**
     * The selected paths, simplification configuration, and overwrite authorization.
     *
     * @param path input GPX file
     * @param outputPath destination file
     * @param config simplification settings in meters
     * @param overwriteApproved whether overwriting the destination was explicitly approved
     */
    public record Parameters(Path path, Path outputPath, SimplificationConfig config, boolean overwriteApproved) {
        /** Validates the required result values. */
        public Parameters {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(outputPath, "outputPath");
            Objects.requireNonNull(config, "config");
        }
    }

    /**
     * Creates a dialog with the simplifier's default settings.
     *
     * @param owner owner window, or null for an unowned dialog
     */
    public SimplifyParametersDialog(Window owner) {
        this(owner, null, null, null);
    }

    /**
     * Creates a dialog with optional remembered paths and settings. Cancel or closing
     * the dialog returns an empty result from {@link #showAndWait()}.
     *
     * @param owner owner window, or null for an unowned dialog
     * @param initialInput previously selected input, or null
     * @param initialOutput previously selected output, or null to suggest a sibling of the input
     * @param initialConfig previously selected settings, or null for defaults
     */
    public SimplifyParametersDialog(Window owner, Path initialInput, Path initialOutput,
                                    SimplificationConfig initialConfig) {
        SimplificationConfig config = initialConfig == null ? SimplificationConfig.defaults() : initialConfig;
        if (owner != null) initOwner(owner);
        setTitle("Simplify GPX");
        getDialogPane().setStyle("-fx-background-color: #2b2b2b;");
        ButtonType execute = new ButtonType("Execute", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(execute, ButtonType.CANCEL);

        Label fileLabel = new Label("Select a GPX file:");
        fileLabel.setStyle(LABEL_STYLE);
        TextField fileField = new TextField();
        fileField.setPromptText("GPX file path");
        if (initialInput != null) fileField.setText(initialInput.toString());
        TextField outputField = new TextField();
        outputField.setPromptText("Output file path");
        if (initialOutput != null) outputField.setText(initialOutput.toString());
        else if (initialInput != null && initialInput.getFileName() != null) {
            outputField.setText(suggestedOutput(initialInput).toString());
        }
        Button browse = new Button("Browse");
        browse.setStyle(BUTTON_STYLE);
        browse.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select GPX File");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("GPX Files", "*.gpx", "*.GPX"));
            File selected = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
            if (selected != null) {
                Path input = selected.toPath().toAbsolutePath();
                fileField.setText(input.toString());
                outputField.setText(suggestedOutput(input).toString());
            }
        });
        HBox fileBox = new HBox(10, fileField, browse);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fileField, Priority.ALWAYS);

        Label outputLabel = new Label("Output file:");
        outputLabel.setStyle(LABEL_STYLE);
        Label stepLabel = new Label("Minimum step (meters):");
        stepLabel.setStyle(LABEL_STYLE);
        TextField stepField = new TextField(Double.toString(config.minStepMeters()));
        Label epsilonLabel = new Label("RDP maximum deviation (meters):");
        epsilonLabel.setStyle(LABEL_STYLE);
        TextField epsilonField = new TextField(Double.toString(config.rdpEpsilonMeters()));
        Label elevationLabel = new Label("Elevation smoothing radius (meters):");
        elevationLabel.setStyle(LABEL_STYLE);
        TextField elevationField = new TextField(Double.toString(config.elevationWindowMeters()));
        Label hint = new Label("Values at or below zero disable the corresponding operation.");
        hint.setStyle(LABEL_STYLE);
        hint.setWrapText(true);
        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ff8080;");
        validation.setWrapText(true);
        VBox content = new VBox(10, fileLabel, fileBox, outputLabel, outputField, stepLabel, stepField,
                epsilonLabel, epsilonField, elevationLabel, elevationField, hint, validation);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        getDialogPane().setContent(content);

        boolean[] overwriteApproved = {false};
        getDialogPane().lookupButton(execute).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            overwriteApproved[0] = false;
            try {
                Path path = Path.of(fileField.getText().trim());
                if (fileField.getText().isBlank() || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                    validation.setText("Choose a readable GPX file.");
                    event.consume();
                } else if (outputField.getText().isBlank()) {
                    validation.setText("Enter an output file path.");
                    event.consume();
                } else {
                    double step = Double.parseDouble(stepField.getText().trim());
                    double epsilon = Double.parseDouble(epsilonField.getText().trim());
                    double elevation = Double.parseDouble(elevationField.getText().trim());
                    if (!Double.isFinite(step) || !Double.isFinite(epsilon) || !Double.isFinite(elevation)) {
                        throw new NumberFormatException();
                    }
                    Path output = Path.of(outputField.getText().trim()).toAbsolutePath().normalize();
                    Path parent = output.getParent();
                    if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent) ||
                            (Files.exists(output) && (!Files.isRegularFile(output) || !Files.isWritable(output)))) {
                        validation.setText("Choose a writable output file in an existing directory.");
                        event.consume();
                    } else if (Files.exists(output)) {
                        ButtonType overwrite = new ButtonType("Overwrite", ButtonBar.ButtonData.OK_DONE);
                        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                                "The output file already exists. Overwrite it?", overwrite, ButtonType.CANCEL);
                        confirmation.initOwner(getDialogPane().getScene().getWindow());
                        confirmation.setTitle("Confirm overwrite");
                        confirmation.setHeaderText(null);
                        overwriteApproved[0] = confirmation.showAndWait().orElse(ButtonType.CANCEL) == overwrite;
                        if (!overwriteApproved[0]) event.consume();
                    }
                }
            } catch (InvalidPathException ex) {
                validation.setText("Enter a valid file path.");
                event.consume();
            } catch (NumberFormatException ex) {
                validation.setText("Enter finite numbers for all simplification settings.");
                event.consume();
            }
        });
        setResultConverter(button -> {
            if (button != execute) return null;
            return new Parameters(Path.of(fileField.getText().trim()), Path.of(outputField.getText().trim()),
                    new SimplificationConfig(Double.parseDouble(stepField.getText().trim()),
                            Double.parseDouble(epsilonField.getText().trim()),
                            Double.parseDouble(elevationField.getText().trim())), overwriteApproved[0]);
        });
    }

    /**
     * Derives the conventional simplified sibling filename from an input path.
     *
     * @param input source file path
     * @return suggested output path
     */
    private static Path suggestedOutput(Path input) {
        String filename = input.getFileName().toString();
        int extension = filename.toLowerCase(Locale.ROOT).endsWith(".gpx") ? filename.length() - 4 : filename.length();
        return input.resolveSibling(filename.substring(0, extension) + "-simplified.gpx");
    }
}