package com.sdcote.gpxlab;

import coyote.commons.log.Log;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Common, thread-safe JavaFX status dialog component for tasks launched across GPXLab.
 *
 * <p>Provides a uniform visual status indicator featuring:
 * <ul>
 *   <li>A percentage-complete progress bar.</li>
 *   <li>A dynamic status footer message showing current activity and progress.</li>
 *   <li>A unified action button that functions as "Cancel" while the task is executing,
 *       and transitions to "Dismiss" when the task completes.</li>
 *   <li>Graceful cancellation handling where pressing "Cancel" sets a thread-safe flag
 *       and grays out the button while the task finishes any in-flight steps before completing.</li>
 *   <li>Guaranteed data protection by disallowing window closure until the task explicitly
 *       signals completion and the user clicks "Dismiss".</li>
 * </ul>
 * </p>
 *
 * <p>Constructors and inherited Stage methods require the JavaFX Application Thread.
 * The factory and task-control methods may be called from other threads after the
 * JavaFX toolkit has started. Callbacks run on the JavaFX Application Thread.</p>
 */
public class StatusDialog extends Stage {

    public static final String DEFAULT_TITLE = "Task Status";
    public static final String BTN_LABEL_CANCEL = "Cancel";
    public static final String BTN_LABEL_DISMISS = "Dismiss";
    public static final String DEFAULT_STATUS_MESSAGE = "Ready";

    private static final int DEFAULT_WIDTH = 500;
    private static final int PADDING = 20;
    private static final int SPACING = 15;

    // Dark theme styling consistent with Navista desktop UI
    private static final String STYLE_ROOT = "-fx-background-color: #2b2b2b; -fx-border-color: #4c5052; -fx-border-width: 1px;";
    private static final String STYLE_HEADER = "-fx-background-color: #3c3f41; -fx-border-color: #4c5052; -fx-border-width: 0 0 1px 0; -fx-padding: 10px 15px; -fx-cursor: move;";
    private static final String STYLE_TITLE = "-fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', sans-serif; -fx-cursor: move;";
    private static final String STYLE_PROGRESS_BAR = "-fx-accent: #365880;";
    private static final String STYLE_FOOTER = "-fx-text-fill: #bbbbbb; -fx-font-size: 12px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_BUTTON_DEFAULT = "-fx-background-color: #3c3f41; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-padding: 8px 18px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_BUTTON_HOVER = "-fx-background-color: #4c5052; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-padding: 8px 18px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_PRIMARY_BUTTON_DEFAULT = "-fx-background-color: #365880; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_PRIMARY_BUTTON_HOVER = "-fx-background-color: #436d9d; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif;";

    private final AtomicBoolean canceled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    private HBox headerBox;
    private Label titleLabel;
    private ProgressBar progressBar;
    private Label footerLabel;
    private Button actionButton;

    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    private volatile double currentProgress = 0.0;
    private volatile String currentStatusMessage = DEFAULT_STATUS_MESSAGE;

    private volatile Runnable onCancelCallback;
    private volatile Runnable onDismissCallback;
    private volatile Runnable onCompleteCallback;

    /**
     * Creates a new status dialog with the default title.
     */
    public StatusDialog() {
        this(null, DEFAULT_TITLE);
    }

    /**
     * Creates a new status dialog with the specified title.
     *
     * @param title The dialog title and header text.
     */
    public StatusDialog(String title) {
        this(null, title);
    }

    /**
     * Creates a new status dialog with the specified owner stage.
     *
     * @param owner The parent {@link Stage} or {@code null}.
     */
    public StatusDialog(Stage owner) {
        this(owner, DEFAULT_TITLE);
    }

    /**
     * Creates a new status dialog with the specified owner stage and title.
     *
     * @param owner The parent {@link Stage} or {@code null}.
     * @param title The dialog title and header text.
     */
    public StatusDialog(Stage owner, String title) {
        super();
        if (owner != null) {
            initOwner(owner);
        }
        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.NONE);
        setAlwaysOnTop(true);
        setTitle(title != null ? title : DEFAULT_TITLE);
        buildUI(title != null ? title : DEFAULT_TITLE);

        // Prevent closing window while task is executing
        setOnCloseRequest(event -> {
            if (!isComplete()) {
                event.consume();
            }
        });
    }

    private void buildUI(String titleText) {
        VBox root = new VBox();
        root.setStyle(STYLE_ROOT);
        root.setPrefWidth(DEFAULT_WIDTH);

        // Header frame providing window title and dragging area to reposition the dialog
        headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setStyle(STYLE_HEADER);

        titleLabel = new Label(titleText != null ? titleText : DEFAULT_TITLE);
        titleLabel.setStyle(STYLE_TITLE);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // Synchronize title changes with header label
        titleProperty().addListener((obs, oldVal, newVal) -> {
            if (titleLabel != null) {
                titleLabel.setText(newVal != null ? newVal : DEFAULT_TITLE);
            }
        });

        headerBox.getChildren().add(titleLabel);

        // Enable moving dialog window by dragging the header
        headerBox.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        headerBox.setOnMouseDragged(event -> {
            setX(event.getScreenX() - dragOffsetX);
            setY(event.getScreenY() - dragOffsetY);
        });

        // Content body container
        VBox contentBox = new VBox(SPACING);
        contentBox.setPadding(new Insets(PADDING));

        progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle(STYLE_PROGRESS_BAR);

        footerLabel = new Label(DEFAULT_STATUS_MESSAGE);
        footerLabel.setStyle(STYLE_FOOTER);
        footerLabel.setWrapText(true);
        footerLabel.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(footerLabel, Priority.ALWAYS);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        actionButton = new Button(BTN_LABEL_CANCEL);
        actionButton.setStyle(STYLE_BUTTON_DEFAULT);
        actionButton.setOnMouseEntered(e -> {
            if (!actionButton.isDisabled()) {
                actionButton.setStyle(completed.get() ? STYLE_PRIMARY_BUTTON_HOVER : STYLE_BUTTON_HOVER);
            }
        });
        actionButton.setOnMouseExited(e -> {
            if (!actionButton.isDisabled()) {
                actionButton.setStyle(completed.get() ? STYLE_PRIMARY_BUTTON_DEFAULT : STYLE_BUTTON_DEFAULT);
            }
        });
        actionButton.setOnAction(e -> handleActionButton());

        buttonBox.getChildren().add(actionButton);

        contentBox.getChildren().addAll(progressBar, footerLabel, buttonBox);
        root.getChildren().addAll(headerBox, contentBox);

        Scene scene = new Scene(root);
        setScene(scene);
        sizeToScene();
        setResizable(false);
    }

    private void handleActionButton() {
        if (!completed.get()) {
            cancel();
        } else {
            // Task is completed: close the dialog
            if (onDismissCallback != null) {
                try {
                    onDismissCallback.run();
                } catch (Exception ex) {
                    Log.error("Error in onDismiss callback: " + ex.getMessage(), ex);
                }
            }
            close();
        }
    }

    /**
     * Checks if the user has requested task cancellation.
     *
     * @return {@code true} if cancel was pressed or triggered; {@code false} otherwise.
     */
    public boolean isCanceled() {
        return canceled.get();
    }

    /**
     * Alias for {@link #isCanceled()}.
     *
     * @return {@code true} if canceled; {@code false} otherwise.
     */
    public boolean isCancelled() {
        return canceled.get();
    }

    /**
     * Programmatically signals cancellation once while the task is running.
     */
    public void cancel() {
        if (completed.get() || !canceled.compareAndSet(false, true)) {
            return;
        }
        runOnFxThread(() -> {
            if (!completed.get()) {
                actionButton.setDisable(true);
            }
            Log.info("Task cancellation requested by user in StatusDialog: " + getTitle());
            Runnable callback = onCancelCallback;
            if (callback != null) {
                try {
                    callback.run();
                } catch (Exception ex) {
                    Log.error("Error in onCancel callback: " + ex.getMessage(), ex);
                }
            }
        });
    }

    /**
     * Checks if the task has completed processing.
     *
     * @return {@code true} if the task called {@link #complete()}; {@code false} otherwise.
     */
    public boolean isComplete() {
        return completed.get();
    }

    /**
     * Alias for {@link #isComplete()}.
     *
     * @return {@code true} if complete; {@code false} otherwise.
     */
    public boolean isCompleted() {
        return completed.get();
    }

    /**
     * Updates the percentage-complete progress bar.
     *
     * @param progress Progress value between 0.0 and 1.0 (or above 1.0 as a percentage). A negative value sets indeterminate progress.
     */
    public void setProgress(double progress) {
        double normalized = progress;
        if (normalized > 1.0) {
            normalized = normalized / 100.0;
        }
        normalized = Double.isNaN(normalized) ? 0.0 : normalized < 0 ? -1.0 : Math.min(normalized, 1.0);
        this.currentProgress = normalized;
        final double finalProgress = normalized;
        runOnFxThread(() -> {
            if (progressBar != null) {
                progressBar.setProgress(finalProgress);
            }
        });
    }

    /**
     * Updates the percentage-complete progress bar based on current step and total steps.
     *
     * @param current Current completed units.
     * @param total   Total units.
     */
    public void setProgress(long current, long total) {
        if (total <= 0) {
            setProgress(0.0);
        } else {
            setProgress(Math.max(0.0, Math.min(1.0, (double) current / (double) total)));
        }
    }

    /**
     * Gets the current progress value.
     *
     * @return Current progress (0.0 to 1.0), or -1.0 when indeterminate.
     */
    public double getProgress() {
        return currentProgress;
    }

    /**
     * Updates the footer status message.
     *
     * @param message Status message text.
     */
    public void setStatusMessage(String message) {
        this.currentStatusMessage = message != null ? message : "";
        runOnFxThread(() -> {
            if (footerLabel != null) {
                footerLabel.setText(this.currentStatusMessage);
                sizeToScene();
            }
        });
    }

    /**
     * Alias for {@link #setStatusMessage(String)}.
     *
     * @param message Status message text.
     */
    public void setStatus(String message) {
        setStatusMessage(message);
    }

    /**
     * Alias for {@link #setStatusMessage(String)}.
     *
     * @param message Status message text.
     */
    public void setMessage(String message) {
        setStatusMessage(message);
    }

    /**
     * Gets the current footer status message.
     *
     * @return Current status message text.
     */
    public String getStatusMessage() {
        return currentStatusMessage;
    }

    /**
     * Updates both the progress bar and status footer message in a single call.
     *
     * @param progress Progress value (0.0 to 1.0 or 0 to 100).
     * @param message  Status message text.
     */
    public void update(double progress, String message) {
        setProgress(progress);
        setStatusMessage(message);
    }

    /**
     * Updates both the progress bar and status footer message in a single call.
     *
     * @param current Current completed units.
     * @param total   Total units.
     * @param message Status message text.
     */
    public void update(long current, long total, String message) {
        setProgress(current, total);
        setStatusMessage(message);
    }

    /**
     * Signals that task processing is complete, transitioning the action button
     * from "Cancel" to "Dismiss" and re-enabling it.
     */
    public void complete() {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        runOnFxThread(() -> {
            if (actionButton != null) {
                actionButton.setText(BTN_LABEL_DISMISS);
                actionButton.setDisable(false);
                actionButton.setStyle(STYLE_PRIMARY_BUTTON_DEFAULT);
            }
            if (onCompleteCallback != null) {
                try {
                    onCompleteCallback.run();
                } catch (Exception ex) {
                    Log.error("Error in onComplete callback: " + ex.getMessage(), ex);
                }
            }
            sizeToScene();
        });
    }

    /**
     * Signals that task processing is complete with a final status message.
     *
     * @param finalStatusMessage Final status text displayed in the footer.
     */
    public void complete(String finalStatusMessage) {
        if (finalStatusMessage != null) {
            setStatusMessage(finalStatusMessage);
        }
        complete();
    }

    /**
     * Signals that task processing failed with a final error message.
     * Transitions the action button to "Dismiss" and updates the footer status.
     *
     * @param failureMessage Error/failure status text displayed in the footer.
     */
    public void fail(String failureMessage) {
        complete(failureMessage != null ? failureMessage : "Task failed.");
    }

    /**
     * Signals that task processing failed.
     */
    public void fail() {
        fail("Task failed.");
    }

    /**
     * Alias for {@link #complete()}.
     */
    public void setComplete() {
        complete();
    }

    /**
     * Alias for {@link #complete()}.
     */
    public void completed() {
        complete();
    }

    /**
     * Alias for {@link #complete()}.
     */
    public void finish() {
        complete();
    }

    /**
     * Creates and returns a new {@link StatusDialog} safely from any thread.
     *
     * @param title The dialog title.
     * @return The created {@link StatusDialog} instance.
     */
    public static StatusDialog create(String title) {
        return create(null, title);
    }

    /**
     * Creates and returns a new {@link StatusDialog} safely from any thread.
     *
     * @param owner The parent {@link Stage} or {@code null}.
     * @param title The dialog title.
     * @return The created {@link StatusDialog} instance.
     */
    public static StatusDialog create(Stage owner, String title) {
        if (Platform.isFxApplicationThread()) {
            return new StatusDialog(owner, title);
        }
        FutureTask<StatusDialog> task = new FutureTask<>(() -> new StatusDialog(owner, title));
        Platform.runLater(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating status dialog", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to create status dialog", e.getCause());
        }
    }

    /**
     * Creates and displays a new {@link StatusDialog} on the JavaFX thread.
     *
     * @param owner The parent {@link Stage} or {@code null}.
     * @param title The dialog title.
     * @return The displayed {@link StatusDialog} instance.
     */
    public static StatusDialog display(Stage owner, String title) {
        StatusDialog dialog = create(owner, title);
        if (dialog != null) {
            dialog.showDialog();
        }
        return dialog;
    }

    /**
     * Displays the status dialog on the JavaFX Application Thread.
     */
    public void showDialog() {
        runOnFxThread(() -> {
            show();
            toFront();
            requestFocus();
        });
    }

    /**
     * Sets a callback to execute when the user clicks Cancel.
     *
     * @param callback Callback runnable.
     */
    public void setOnCancel(Runnable callback) {
        this.onCancelCallback = callback;
    }

    /**
     * Sets a callback to execute when the user clicks Dismiss.
     *
     * @param callback Callback runnable.
     */
    public void setOnDismiss(Runnable callback) {
        this.onDismissCallback = callback;
    }

    /**
     * Sets a callback to execute when the task completes.
     *
     * @param callback Callback runnable.
     */
    public void setOnComplete(Runnable callback) {
        this.onCompleteCallback = callback;
    }

    public HBox getHeaderBox() {
        return headerBox;
    }

    public ProgressBar getProgressBar() {
        return progressBar;
    }

    public Label getFooterLabel() {
        return footerLabel;
    }

    public Label getStatusLabel() {
        return footerLabel;
    }

    public Label getTitleLabel() {
        return titleLabel;
    }

    public Button getActionButton() {
        return actionButton;
    }

    /**
     * Helper to safely execute actions on the JavaFX Application Thread.
     *
     * @param action Action to execute.
     */
    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
