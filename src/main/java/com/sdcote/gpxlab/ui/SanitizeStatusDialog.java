package com.sdcote.gpxlab.ui;

import coyote.commons.ExceptionUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Objects;

/** Non-modal progress window for a GPX sanitization operation. */
public final class SanitizeStatusDialog {
    private final Stage stage;
    private final TextArea output;
    private final Label status;
    private final ProgressIndicator progress;
    private final Button close;

    /**
     * Creates the window on the JavaFX application thread. Call {@link #show()} before
     * starting work; worker threads may call the update methods.
     *
     * @param owner owner window, or null for an unowned window
     */
    public SanitizeStatusDialog(Window owner) {
        stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Sanitize GPX Status");
        status = new Label("Sanitizing GPX…");
        status.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px;");
        progress = new ProgressIndicator();
        progress.setPrefSize(24, 24);
        progress.setMaxSize(24, 24);
        HBox heading = new HBox(10, progress, status);
        heading.setAlignment(Pos.CENTER_LEFT);
        output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        VBox.setVgrow(output, Priority.ALWAYS);
        close = new Button("Close");
        close.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: #ffffff;");
        close.setOnAction(event -> stage.close());
        HBox buttons = new HBox(close);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox root = new VBox(15, heading, output, buttons);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #2b2b2b;");
        stage.setScene(new Scene(root, 600, 400));
    }

    /** Shows the window; must be called on the JavaFX application thread. */
    public void show() {
        stage.show();
    }

    /**
     * Appends text exactly as supplied; may be called from any thread.
     *
     * @param text text to append, including any desired line separator
     */
    public void append(String text) {
        Objects.requireNonNull(text, "text");
        onFxThread(() -> output.appendText(text));
    }

    /**
     * Displays a completion message and stops the progress indicator; may be called from any thread.
     *
     * @param message completion message
     */
    public void complete(String message) {
        Objects.requireNonNull(message, "message");
        onFxThread(() -> {
            status.setText(message);
            progress.setVisible(false);
        });
    }

    /**
     * Displays an error and its stack trace in the window; may be called from any thread.
     *
     * @param message description of the failure
     * @param error cause of the failure
     */
    public void fail(String message, Throwable error) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(error, "error");
        String details = ExceptionUtil.stackTrace(error);
        onFxThread(() -> {
            status.setText(message);
            status.setStyle("-fx-text-fill: #ff8080; -fx-font-size: 14px;");
            progress.setVisible(false);
            output.appendText(message + System.lineSeparator() + details + System.lineSeparator());
        });
    }

    /**
     * Runs a UI update immediately on the JavaFX thread or queues it from a worker.
     *
     * @param update update to run
     */
    private static void onFxThread(Runnable update) {
        if (Platform.isFxApplicationThread()) update.run();
        else Platform.runLater(update);
    }
}