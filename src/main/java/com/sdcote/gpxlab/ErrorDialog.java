package com.sdcote.gpxlab;

import coyote.commons.StringUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Common modal JavaFX error dialog component for alerting users to catastrophic,
 * unrecoverable task failures across GPXLab.
 *
 * <p>Provides a standardized, high-visibility error presentation featuring:
 * <ul>
 *   <li>Modal execution blocking interaction with parent windows until dismissed.</li>
 *   <li>Prominent error badge glyph and header banner distinguishing it from informational dialogs.</li>
 *   <li>A concise summary header stating that processing failed and no output was generated.</li>
 *   <li>A clear, context-specific root cause description (e.g. file lock, missing worksheet, corrupt data).</li>
 *   <li>A tabbed layout with primary "Error" tab selected by default and an optional "Stacktrace" tab when an exception is present.</li>
 *   <li>Read-only stacktrace text area supporting full text selection, copy shortcuts, and a dedicated copy button.</li>
 *   <li>Thread-safe display and mutation methods callable from any thread (background worker or UI).</li>
 * </ul>
 * </p>
 * <p>Constructors and inherited Stage methods require the JavaFX Application Thread.
 * The static show methods and the explicitly documented mutation methods may be called
 * from other threads after the JavaFX toolkit has started.</p>
 */
public class ErrorDialog extends Stage {

    public static final String DEFAULT_TITLE = "Task Error";
    public static final String DEFAULT_SUMMARY_HEADER = "Processing failed. No output was generated.";
    public static final String BTN_LABEL_DISMISS = "Dismiss";
    public static final String BTN_LABEL_COPY = "Copy to Clipboard";
    public static final String TAB_TITLE_ERROR = "Error";
    public static final String TAB_TITLE_STACKTRACE = "Stacktrace";

    private static final int DEFAULT_WIDTH = 560;
    private static final int PADDING = 16;
    private static final int SPACING = 12;

    // Dark theme error styling consistent with Navista desktop UI
    private static final String STYLE_ROOT = "-fx-background-color: #2b2b2b; -fx-border-color: #d9534f; -fx-border-width: 1.5px;";
    private static final String STYLE_HEADER = "-fx-background-color: #3c3f41; -fx-border-color: #d9534f; -fx-border-width: 0 0 1px 0; -fx-padding: 10px 15px; -fx-cursor: move;";
    private static final String STYLE_TITLE = "-fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', sans-serif; -fx-cursor: move;";
    private static final String STYLE_SUMMARY_HEADER = "-fx-text-fill: #ff6b6b; -fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_ROOT_CAUSE = "-fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-font-family: 'Segoe UI', sans-serif; -fx-line-spacing: 2px;";
    private static final String STYLE_BUTTON_DEFAULT = "-fx-background-color: #3c3f41; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-padding: 8px 20px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold;";
    private static final String STYLE_BUTTON_HOVER = "-fx-background-color: #4c5052; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-padding: 8px 20px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold;";
    private static final String STYLE_BUTTON_SECONDARY = "-fx-background-color: #383b3d; -fx-text-fill: #e0e0e0; -fx-font-size: 12px; -fx-padding: 5px 12px; -fx-cursor: hand; -fx-background-radius: 3px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_BUTTON_SECONDARY_HOVER = "-fx-background-color: #484b4d; -fx-text-fill: #ffffff; -fx-font-size: 12px; -fx-padding: 5px 12px; -fx-cursor: hand; -fx-background-radius: 3px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_TAB_PANE = "-fx-background-color: #2b2b2b; -fx-tab-min-width: 90px;";
    private static final String STYLE_TEXT_AREA = "-fx-control-inner-background: #1e1e1e; -fx-background-color: #1e1e1e; -fx-text-fill: #f0f0f0; -fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11px; -fx-highlight-fill: #365880; -fx-highlight-text-fill: #ffffff;";

    public static final String STYLESHEET_RESOURCE_PATH = "/styles/error-dialog.css";
    public static final String TAB_PANE_DARK_CSS = """
        .tab-pane {
            -fx-background-color: #2b2b2b;
            -fx-tab-min-width: 90px;
        }
        .tab-pane > .tab-header-area {
            -fx-background-color: #2b2b2b;
            -fx-padding: 0 0 2 0;
        }
        .tab-pane > .tab-header-area > .tab-header-background {
            -fx-background-color: #2b2b2b;
        }
        .tab-pane > .tab-header-area > .headers-region > .tab {
            -fx-background-color: #383b3d;
            -fx-background-insets: 0;
            -fx-background-radius: 4 4 0 0;
            -fx-border-color: #4c5052;
            -fx-border-width: 1 1 0 1;
            -fx-border-radius: 4 4 0 0;
            -fx-padding: 6 16 6 16;
            -fx-cursor: hand;
        }
        .tab-pane > .tab-header-area > .headers-region > .tab .tab-label {
            -fx-text-fill: #cccccc;
            -fx-font-family: 'Segoe UI', sans-serif;
            -fx-font-size: 12px;
            -fx-font-weight: normal;
        }
        .tab-pane > .tab-header-area > .headers-region > .tab:hover {
            -fx-background-color: #484b4d;
        }
        .tab-pane > .tab-header-area > .headers-region > .tab:hover .tab-label {
            -fx-text-fill: #ffffff;
        }
        .tab-pane > .tab-header-area > .headers-region > .tab:selected {
            -fx-background-color: #2b2b2b;
            -fx-border-color: #d9534f #4c5052 #2b2b2b #4c5052;
            -fx-border-width: 2 1 1 1;
        }
        .tab-pane > .tab-header-area > .headers-region > .tab:selected .tab-label {
            -fx-text-fill: #ffffff;
            -fx-font-weight: bold;
        }
        .tab-pane > .tab-content-area {
            -fx-background-color: #2b2b2b;
            -fx-border-color: #4c5052;
            -fx-border-width: 1 0 0 0;
            -fx-padding: 4 0 0 0;
        }
        .tab-pane:focused > .tab-header-area > .headers-region > .tab:selected .focus-indicator {
            -fx-border-style: none;
            -fx-border-width: 0;
        }
        """;

    private HBox headerBox;
    private Label titleLabel;
    private Label summaryHeaderLabel;
    private Label rootCauseLabel;
    private TabPane tabPane;
    private Tab errorTab;
    private Tab stacktraceTab;
    private TextArea stacktraceTextArea;
    private Button copyButton;
    private Button dismissButton;

    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    private String summaryHeaderText;
    private String rootCauseText;
    private volatile String stacktraceText;
    private volatile Throwable exception;

    /**
     * Constructs a default error dialog.
     */
    public ErrorDialog() {
        this(null, DEFAULT_TITLE, DEFAULT_SUMMARY_HEADER, "An unrecoverable error occurred during task execution.", (Throwable) null);
    }

    /**
     * Constructs an error dialog with a root cause message and exception.
     *
     * @param rootCause Message describing the root cause of the failure.
     * @param throwable The associated {@link Throwable} or {@code null}.
     */
    public ErrorDialog(String rootCause, Throwable throwable) {
        this(null, DEFAULT_TITLE, DEFAULT_SUMMARY_HEADER, rootCause, throwable);
    }

    /**
     * Constructs an error dialog with a custom title, root cause message, and exception.
     *
     * @param title     Dialog title.
     * @param rootCause Message describing the root cause of the failure.
     * @param throwable The associated {@link Throwable} or {@code null}.
     */
    public ErrorDialog(String title, String rootCause, Throwable throwable) {
        this(null, title, DEFAULT_SUMMARY_HEADER, rootCause, throwable);
    }

    /**
     * Constructs an error dialog with a parent stage, title, summary header, root cause message, and raw stacktrace text.
     *
     * @param owner          The parent {@link Stage} or {@code null}.
     * @param title          Dialog title.
     * @param summaryHeader  Concise failure summary header.
     * @param rootCause      Message describing the root cause of the failure.
     * @param stacktraceText Raw stacktrace text or {@code null}.
     */
    public ErrorDialog(Stage owner, String title, String summaryHeader, String rootCause, String stacktraceText) {
        super();
        this.summaryHeaderText = StringUtil.isNotBlank(summaryHeader) ? summaryHeader : DEFAULT_SUMMARY_HEADER;
        this.rootCauseText = StringUtil.isNotBlank(rootCause) ? rootCause : "An unrecoverable error occurred.";
        this.stacktraceText = stacktraceText;
        this.exception = null;

        initDialogWindow(owner, title);
    }

    /**
     * Constructs an error dialog with a parent stage, title, summary header, root cause message, and exception.
     *
     * @param owner         The parent {@link Stage} or {@code null}.
     * @param title         Dialog title.
     * @param summaryHeader Concise failure summary header.
     * @param rootCause     Message describing the root cause of the failure.
     * @param throwable     The associated {@link Throwable} or {@code null}.
     */
    public ErrorDialog(Stage owner, String title, String summaryHeader, String rootCause, Throwable throwable) {
        super();
        this.summaryHeaderText = StringUtil.isNotBlank(summaryHeader) ? summaryHeader : DEFAULT_SUMMARY_HEADER;
        this.rootCauseText = StringUtil.isNotBlank(rootCause) ? rootCause : determineRootCause(throwable);
        this.exception = throwable;
        this.stacktraceText = throwable != null ? formatStackTrace(throwable) : null;

        initDialogWindow(owner, title);
    }

    private void initDialogWindow(Stage owner, String title) {
        if (owner != null) {
            initOwner(owner);
            initModality(Modality.WINDOW_MODAL);
        } else {
            initModality(Modality.APPLICATION_MODAL);
        }

        initStyle(StageStyle.UNDECORATED);
        setAlwaysOnTop(true);
        setTitle(title != null ? title : DEFAULT_TITLE);
        buildUI(title != null ? title : DEFAULT_TITLE);
    }

    private void buildUI(String titleText) {
        VBox root = new VBox();
        root.setStyle(STYLE_ROOT);
        root.setPrefWidth(DEFAULT_WIDTH);

        // Header frame providing window title and dragging area
        headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setStyle(STYLE_HEADER);

        titleLabel = new Label(titleText != null ? titleText : DEFAULT_TITLE);
        titleLabel.setStyle(STYLE_TITLE);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

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

        // TabPane setup
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle(STYLE_TAB_PANE);
        applyDarkTabStyles(tabPane, null);

        // Tab 1: "Error"
        errorTab = new Tab(TAB_TITLE_ERROR);
        errorTab.setClosable(false);

        VBox errorTabContent = new VBox(12);
        errorTabContent.setPadding(new Insets(14, 10, 14, 10));

        // Top row with Error Icon Badge and Text summaries
        HBox messageRow = new HBox(16);
        messageRow.setAlignment(Pos.TOP_LEFT);

        // Standardized Error Badge Glyph (high-visibility red circle with white '!')
        HBox errorBadge = createErrorBadge();
        HBox.setHgrow(errorBadge, Priority.NEVER);

        VBox textContainer = new VBox(8);
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        summaryHeaderLabel = new Label(summaryHeaderText);
        summaryHeaderLabel.setStyle(STYLE_SUMMARY_HEADER);
        summaryHeaderLabel.setWrapText(true);
        summaryHeaderLabel.setMaxWidth(Double.MAX_VALUE);

        rootCauseLabel = new Label(rootCauseText);
        rootCauseLabel.setStyle(STYLE_ROOT_CAUSE);
        rootCauseLabel.setWrapText(true);
        rootCauseLabel.setMaxWidth(Double.MAX_VALUE);

        textContainer.getChildren().addAll(summaryHeaderLabel, rootCauseLabel);
        messageRow.getChildren().addAll(errorBadge, textContainer);

        errorTabContent.getChildren().add(messageRow);
        errorTab.setContent(errorTabContent);

        // Tab 2: "Stacktrace"
        stacktraceTab = new Tab(TAB_TITLE_STACKTRACE);
        stacktraceTab.setClosable(false);

        VBox stacktraceTabContent = new VBox(8);
        stacktraceTabContent.setPadding(new Insets(10, 10, 10, 10));

        HBox stacktraceTopBar = new HBox(8);
        stacktraceTopBar.setAlignment(Pos.CENTER_RIGHT);

        copyButton = new Button(BTN_LABEL_COPY);
        copyButton.setStyle(STYLE_BUTTON_SECONDARY);
        copyButton.setOnMouseEntered(e -> copyButton.setStyle(STYLE_BUTTON_SECONDARY_HOVER));
        copyButton.setOnMouseExited(e -> copyButton.setStyle(STYLE_BUTTON_SECONDARY));
        copyButton.setOnAction(e -> copyStacktraceToClipboard());

        stacktraceTopBar.getChildren().add(copyButton);

        stacktraceTextArea = new TextArea();
        stacktraceTextArea.setEditable(false);
        stacktraceTextArea.setWrapText(true);
        stacktraceTextArea.setPrefRowCount(12);
        stacktraceTextArea.setStyle(STYLE_TEXT_AREA);
        VBox.setVgrow(stacktraceTextArea, Priority.ALWAYS);

        stacktraceTabContent.getChildren().addAll(stacktraceTopBar, stacktraceTextArea);
        stacktraceTab.setContent(stacktraceTabContent);

        // Add Tab 1 by default
        tabPane.getTabs().add(errorTab);

        // Add Tab 2 if stacktrace text is present
        if (StringUtil.isNotBlank(stacktraceText)) {
            stacktraceTextArea.setText(stacktraceText);
            tabPane.getTabs().add(stacktraceTab);
        }

        tabPane.getSelectionModel().select(errorTab);

        // Action Buttons Row
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.BOTTOM_RIGHT);
        buttonBox.setPadding(new Insets(6, 0, 0, 0));

        dismissButton = new Button(BTN_LABEL_DISMISS);
        dismissButton.setStyle(STYLE_BUTTON_DEFAULT);
        dismissButton.setOnMouseEntered(e -> dismissButton.setStyle(STYLE_BUTTON_HOVER));
        dismissButton.setOnMouseExited(e -> dismissButton.setStyle(STYLE_BUTTON_DEFAULT));
        dismissButton.setOnAction(e -> close());

        buttonBox.getChildren().add(dismissButton);

        contentBox.getChildren().addAll(tabPane, buttonBox);
        root.getChildren().addAll(headerBox, contentBox);

        Scene scene = new Scene(root);
        applyDarkTabStyles(tabPane, scene);
        setScene(scene);
        sizeToScene();
        setResizable(false);
    }

    private static HBox createErrorBadge() {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(42, 42);
        box.setMinSize(42, 42);
        box.setMaxSize(42, 42);

        javafx.scene.layout.StackPane stack = new javafx.scene.layout.StackPane();
        Circle circle = new Circle(18);
        circle.setFill(Color.web("#d9534f"));
        circle.setStroke(Color.web("#ff6b6b"));
        circle.setStrokeWidth(1.5);

        Text exclamation = new Text("!");
        exclamation.setFill(Color.WHITE);
        exclamation.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 22));

        stack.getChildren().addAll(circle, exclamation);
        box.getChildren().add(stack);
        return box;
    }

    /**
     * Copies the current stacktrace text to the system clipboard safely on the JavaFX thread.
     */
    public void copyStacktraceToClipboard() {
        runOnFxThread(() -> {
            String text = stacktraceTextArea != null ? stacktraceTextArea.getText() : stacktraceText;
            if (StringUtil.isNotBlank(text)) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                clipboard.setContent(content);
            }
        });
    }

    /**
     * Sets the exception whose stack trace is to be displayed on the "Stacktrace" tab.
     * Extracts the formatted stack trace and updates or clears the secondary tab.
     *
     * @param throwable The {@link Throwable} to extract the stack trace from, or {@code null} to clear.
     */
    public void setStacktrace(Throwable throwable) {
        String text = throwable != null ? formatStackTrace(throwable) : null;
        runOnFxThread(() -> {
            this.exception = throwable;
            setStacktraceText(text);
        });
    }

    /**
     * Sets the exception whose stack trace is to be displayed on the "Stacktrace" tab.
     * Overloaded method for standard {@link Exception} instances.
     *
     * @param exception The {@link Exception} to extract the stack trace from, or {@code null} to clear.
     */
    public void setStacktrace(Exception exception) {
        setStacktrace((Throwable) exception);
    }

    /**
     * Sets raw stack trace text to be displayed on the "Stacktrace" tab.
     * If the text is null or blank, the stack trace tab is cleared and removed.
     *
     * @param stacktraceText The formatted stack trace text, or {@code null} / blank to remove the tab.
     */
    public void setStacktrace(String stacktraceText) {
        runOnFxThread(() -> {
            this.exception = null;
            setStacktraceText(stacktraceText);
        });
    }

    /** Updates the stored trace and its controls on the JavaFX Application Thread.
     * @param stacktraceText trace text, or null to remove it
     */
    private void setStacktraceText(String stacktraceText) {
        this.stacktraceText = stacktraceText;
        if (StringUtil.isBlank(stacktraceText)) {
            if (stacktraceTextArea != null) {
                stacktraceTextArea.clear();
            }
            if (tabPane != null && stacktraceTab != null) {
                tabPane.getTabs().remove(stacktraceTab);
            }
        } else {
            if (stacktraceTextArea != null) {
                stacktraceTextArea.setText(stacktraceText);
            }
            if (tabPane != null && stacktraceTab != null && !tabPane.getTabs().contains(stacktraceTab)) {
                tabPane.getTabs().add(stacktraceTab);
            }
        }
        if (tabPane != null && errorTab != null) {
            tabPane.getSelectionModel().select(errorTab);
        }
        sizeToScene();
    }

    /**
     * Formats the complete stack trace of the provided throwable as a string.
     *
     * @param throwable The {@link Throwable} to format.
     * @return Formatted stack trace string.
     */
    public static String formatStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /**
     * Analyzes a throwable and optional target file to determine a clean, human-readable root cause description.
     *
     * @param throwable The error encountered.
     * @param file      The file being accessed, or {@code null}.
     * @return Human-readable root cause explanation.
     */
    public static String determineRootCause(Throwable throwable, File file) {
        if (throwable == null) {
            return "An unexpected error occurred during processing.";
        }
        Throwable root = throwable;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(root);
        while (root.getCause() != null && visited.add(root.getCause())) {
            root = root.getCause();
        }

        String msg = root.getMessage() != null ? root.getMessage() : throwable.getMessage();
        String fileName = file != null ? file.getName() : "the target file";

        // 1. File lock / process sharing violation
        if (root instanceof java.nio.channels.OverlappingFileLockException || (msg != null && (msg.contains("being used by another process")
                || msg.contains("used by another process")
                || msg.contains("Access is denied")
                || msg.contains("Permission denied")
                || msg.contains("Sharing violation")
                || msg.contains("locked")))) {
            return "File access locked: '" + fileName + "' is currently open or locked by another application (such as Microsoft Excel). Please close the file and try again.";
        }

        // 2. File not found
        if (root instanceof java.io.FileNotFoundException || (msg != null && msg.toLowerCase().contains("does not exist"))) {
            return "File not found: Unable to locate '" + fileName + "'. Please verify the file path exists.";
        }

        // 3. Corrupt or invalid workbook / Office format
        String className = root.getClass().getName();
        if (className.contains("NotOfficeXmlFileException")
                || className.contains("OLE2NotOfficeXmlFileException")
                || className.contains("InvalidFormatException")
                || className.contains("POIXMLException")
                || className.contains("EncryptedDocumentException")
                || root instanceof java.util.zip.ZipException) {
            return "Corrupt or invalid file format: '" + fileName + "' is not a recognized or valid Excel workbook.";
        }

        // 4. Missing or empty sheet
        if (msg != null && (msg.toLowerCase().contains("sheet") || msg.toLowerCase().contains("worksheet"))) {
            return "Worksheet error: " + msg;
        }

        // 5. Template or JSON error
        if (msg != null && (msg.toLowerCase().contains("json") || msg.toLowerCase().contains("template") || msg.toLowerCase().contains("pipeline"))) {
            return "Template processing error: " + msg;
        }

        if (StringUtil.isNotBlank(msg)) {
            return msg;
        }

        return root.getClass().getSimpleName() + " occurred during processing.";
    }

    /**
     * Analyzes a throwable to determine a clean, human-readable root cause description.
     *
     * @param throwable The error encountered.
     * @return Human-readable root cause explanation.
     */
    public static String determineRootCause(Throwable throwable) {
        return determineRootCause(throwable, null);
    }

    /**
     * Creates and displays an {@link ErrorDialog} on the JavaFX Application Thread.
     *
     * @param owner         The parent {@link Stage} or {@code null}.
     * @param title         Dialog title.
     * @param summaryHeader Concise failure summary header.
     * @param rootCause     Root cause description.
     * @param throwable     The associated exception or {@code null}.
     * @return The displayed {@link ErrorDialog} instance.
     */
    public static ErrorDialog show(Stage owner, String title, String summaryHeader, String rootCause, Throwable throwable) {
        if (Platform.isFxApplicationThread()) {
            ErrorDialog dialog = new ErrorDialog(owner, title, summaryHeader, rootCause, throwable);
            dialog.showDialog();
            return dialog;
        }

        FutureTask<ErrorDialog> task = new FutureTask<>(() -> {
            ErrorDialog dialog = new ErrorDialog(owner, title, summaryHeader, rootCause, throwable);
            dialog.showDialog();
            return dialog;
        });
        Platform.runLater(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while showing error dialog", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to show error dialog", e.getCause());
        }
    }

    /**
     * Displays an error dialog with default summary header.
     *
     * @param owner     The parent {@link Stage} or {@code null}.
     * @param title     Dialog title.
     * @param rootCause Root cause description.
     * @param throwable The associated exception or {@code null}.
     * @return The displayed {@link ErrorDialog} instance.
     */
    public static ErrorDialog show(Stage owner, String title, String rootCause, Throwable throwable) {
        return show(owner, title, DEFAULT_SUMMARY_HEADER, rootCause, throwable);
    }

    /**
     * Displays an error dialog with default summary header.
     *
     * @param title     Dialog title.
     * @param rootCause Root cause description.
     * @param throwable The associated exception or {@code null}.
     * @return The displayed {@link ErrorDialog} instance.
     */
    public static ErrorDialog show(String title, String rootCause, Throwable throwable) {
        return show(null, title, DEFAULT_SUMMARY_HEADER, rootCause, throwable);
    }

    /**
     * Displays an error dialog resolving root cause from the exception.
     *
     * @param title     Dialog title.
     * @param throwable The associated exception.
     * @return The displayed {@link ErrorDialog} instance.
     */
    public static ErrorDialog show(String title, Throwable throwable) {
        return show(null, title, DEFAULT_SUMMARY_HEADER, determineRootCause(throwable), throwable);
    }

    /**
     * Displays the error dialog on the JavaFX Application Thread.
     */
    public void showDialog() {
        runOnFxThread(() -> {
            if (tabPane != null && errorTab != null) {
                tabPane.getSelectionModel().select(errorTab);
                if (errorTab.getContent() != null) {
                    errorTab.getContent().requestFocus();
                }
            }
            show();
            toFront();
            requestFocus();
        });
    }

    public Label getTitleLabel() {
        return titleLabel;
    }

    public Label getSummaryHeaderLabel() {
        return summaryHeaderLabel;
    }

    public Label getRootCauseLabel() {
        return rootCauseLabel;
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    public Tab getErrorTab() {
        return errorTab;
    }

    public Tab getStacktraceTab() {
        return stacktraceTab;
    }

    public TextArea getStacktraceTextArea() {
        return stacktraceTextArea;
    }

    /**
     * Backward-compatible alias for {@link #getStacktraceTextArea()}.
     *
     * @return The stacktrace text area.
     */
    public TextArea getDetailsTextArea() {
        return stacktraceTextArea;
    }

    public Button getCopyButton() {
        return copyButton;
    }

    public Button getCopyToClipboardButton() {
        return copyButton;
    }

    public Button getDismissButton() {
        return dismissButton;
    }

    public String getSummaryHeaderText() {
        return summaryHeaderText;
    }

    public String getRootCauseText() {
        return rootCauseText;
    }

    public String getStacktraceText() {
        return stacktraceText;
    }

    public Throwable getException() {
        return exception;
    }

    /**
     * Applies dark "night" theme stylesheet styling to the TabPane and Scene so that
     * tabs blend seamlessly with the rest of the dark dialog while retaining high legibility.
     *
     * @param pane  The {@link TabPane} to style.
     * @param scene The optional {@link Scene} to style.
     */
    public static void applyDarkTabStyles(TabPane pane, Scene scene) {
        java.net.URL resource = ErrorDialog.class.getResource(STYLESHEET_RESOURCE_PATH);
        if (resource == null) {
            return;
        }
        String cssUrl = resource.toExternalForm();

        if (pane != null && !pane.getStylesheets().contains(cssUrl)) {
            pane.getStylesheets().add(cssUrl);
        }
        if (scene != null && !scene.getStylesheets().contains(cssUrl)) {
            scene.getStylesheets().add(cssUrl);
        }
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
