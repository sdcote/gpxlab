package com.sdcote.gpxlab.ui;

import coyote.commons.ExceptionUtil;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Displays an error and its diagnostic details. */
public final class ErrorDialog {
    /** Prevents construction. */
    private ErrorDialog() { }

    /**
     * Shows the error on the JavaFX application thread.
     *
     * @param owner the owner window, or null
     * @param message a description of the failure
     * @param error the underlying failure
     */
    public static void show(Window owner, String message, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Sanitize GPX Error");
        alert.setHeaderText(message);
        alert.setContentText(error.getMessage());
        TextArea details = new TextArea(ExceptionUtil.stackTrace(error));
        details.setEditable(false);
        details.setWrapText(true);
        alert.getDialogPane().setExpandableContent(details);
        alert.showAndWait();
    }
}