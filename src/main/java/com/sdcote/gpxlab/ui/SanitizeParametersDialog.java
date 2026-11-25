package com.sdcote.gpxlab.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Dialog for choosing a GPX file and the sanitizers to apply. Construct and show on the JavaFX thread. */
public final class SanitizeParametersDialog extends Dialog<SanitizeParametersDialog.Parameters> {
    private static final String LABEL_STYLE = "-fx-text-fill: #ffffff;";
    private static final String BUTTON_STYLE = "-fx-background-color: #3c3f41; -fx-text-fill: #ffffff;";

    /**
     * The chosen files and zero-based indexes into the sanitizer list supplied to the dialog.
     *
     * @param path selected GPX file
     * @param outputPath destination file
     * @param sanitizerIndexes selected indexes in their original order
     * @param overwriteApproved whether overwriting the destination was explicitly approved
     */
    public record Parameters(Path path, Path outputPath, List<Integer> sanitizerIndexes, boolean overwriteApproved) {
        /** Creates an immutable result. */
        public Parameters {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(outputPath, "outputPath");
            sanitizerIndexes = List.copyOf(sanitizerIndexes);
        }

        /**
         * Creates parameters with the conventional sibling output path.
         *
         * @param path selected GPX file
         * @param sanitizerIndexes selected sanitizer indexes
         */
        public Parameters(Path path, List<Integer> sanitizerIndexes) {
            this(path, suggestedOutput(path), sanitizerIndexes, false);
        }

        /**
         * Creates parameters for a destination without authorizing overwrite.
         *
         * @param path selected GPX file
         * @param outputPath destination file
         * @param sanitizerIndexes selected sanitizer indexes
         */
        public Parameters(Path path, Path outputPath, List<Integer> sanitizerIndexes) {
            this(path, outputPath, sanitizerIndexes, false);
        }
    }

    /**
     * Creates a dialog with all sanitizers initially selected. Cancel or closing the dialog
     * returns an empty result from {@link #showAndWait()}.
     *
     * @param owner owner window, or null for an unowned dialog
     * @param sanitizerNames display names in the order used for returned indexes
     */
    public SanitizeParametersDialog(Window owner, List<String> sanitizerNames) {
        this(owner, sanitizerNames, null, null, null);
    }

    /**
     * Creates a dialog with optional previously selected values.
     *
     * @param owner owner window, or null for an unowned dialog
     * @param sanitizerNames display names in the order used for returned indexes
     * @param initialInput previously selected input, or null
     * @param initialOutput previously selected output, or null
     * @param initialSanitizerIndexes previously selected indexes, or null to select all
     * @throws IllegalArgumentException if a remembered index is outside the sanitizer list
     */
    public SanitizeParametersDialog(Window owner, List<String> sanitizerNames, Path initialInput,
                                    Path initialOutput, List<Integer> initialSanitizerIndexes) {
        List<String> names = List.copyOf(sanitizerNames);
        List<Integer> initialIndexes = initialSanitizerIndexes == null ? null : List.copyOf(initialSanitizerIndexes);
        if (initialIndexes != null && initialIndexes.stream().anyMatch(index -> index < 0 || index >= names.size())) {
            throw new IllegalArgumentException("Invalid sanitizer index");
        }
        if (owner != null) initOwner(owner);
        setTitle("Sanitize GPX");
        getDialogPane().setStyle("-fx-background-color: #2b2b2b;");
        ButtonType sanitize = new ButtonType("Execute", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(sanitize, ButtonType.CANCEL);

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
        javafx.scene.control.Button browse = new javafx.scene.control.Button("Browse");
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

        Label sanitizerLabel = new Label("Sanitizers:");
        sanitizerLabel.setStyle(LABEL_STYLE);
        VBox choices = new VBox(8);
        choices.setPadding(new Insets(8));
        choices.setStyle("-fx-background-color: #2b2b2b;");
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            CheckBox checkBox = new CheckBox(names.get(i));
            checkBox.setStyle(LABEL_STYLE);
            checkBox.setSelected(initialIndexes == null || initialIndexes.contains(i));
            checkBoxes.add(checkBox);
            choices.getChildren().add(checkBox);
        }
        ScrollPane scroll = new ScrollPane(choices);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(180);
        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ff8080;");
        validation.setWrapText(true);
        Label outputLabel = new Label("Output file:");
        outputLabel.setStyle(LABEL_STYLE);
        VBox content = new VBox(10, fileLabel, fileBox, outputLabel, outputField, sanitizerLabel, scroll, validation);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        getDialogPane().setContent(content);

        boolean[] overwriteApproved = {false};
        getDialogPane().lookupButton(sanitize).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            overwriteApproved[0] = false;
            try {
                Path path = Path.of(fileField.getText().trim());
                if (fileField.getText().isBlank() || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                    validation.setText("Choose a readable GPX file.");
                    event.consume();
                } else if (outputField.getText().isBlank()) {
                    validation.setText("Enter an output file path.");
                    event.consume();
                } else if (checkBoxes.stream().noneMatch(CheckBox::isSelected)) {
                    validation.setText("Select at least one sanitizer.");
                    event.consume();
                } else {
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
            }
        });
        setResultConverter(button -> {
            if (button != sanitize) return null;
            List<Integer> selected = new ArrayList<>();
            for (int i = 0; i < checkBoxes.size(); i++) {
                if (checkBoxes.get(i).isSelected()) selected.add(i);
            }
            return new Parameters(Path.of(fileField.getText().trim()), Path.of(outputField.getText().trim()), selected,
                    overwriteApproved[0]);
        });
    }

    /**
     * Derives the conventional sanitized sibling filename from an input path.
     *
     * @param input source file path
     * @return suggested output path
     */
    private static Path suggestedOutput(Path input) {
        String filename = input.getFileName().toString();
        int extension = filename.toLowerCase(Locale.ROOT).endsWith(".gpx") ? filename.length() - 4 : filename.length();
        return input.resolveSibling(filename.substring(0, extension) + "-sanitized.gpx");
    }
}