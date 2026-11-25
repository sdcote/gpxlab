package com.sdcote.gpxlab;

import coyote.commons.StringUtil;
import coyote.commons.cfg.Config;
import coyote.commons.dataframe.DataField;
import coyote.commons.dataframe.DataFrame;
import coyote.commons.log.Log;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class LauncherApp extends Application {
    static final String LOGGER_TAG = "Logger";
    static final String CATEGORIES_TAG = "categories";
    static final String VAULT_FILE_TAG = "vaultfile";
    static final String ROOT_STYLE = "-fx-background-color: #2b2b2b;";
    static final String LABEL_STYLE = "-fx-text-fill: #ffffff; -fx-font-size: 24px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', sans-serif;";
    static final String BUTTON_STYLE = "-fx-background-color: #3c3f41; -fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-padding: 15px; -fx-cursor: hand; -fx-background-radius: 5px;";
    static final String BUTTON_HOVER_STYLE = "-fx-background-color: #4c5052; -fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-padding: 15px; -fx-cursor: hand; -fx-background-radius: 5px;";
    static final String MENU_STYLE = "-fx-base: #3c3f41; -fx-background-color: #3c3f41; -fx-border-color: #4c5052; -fx-border-width: 0 0 1px 0; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px;";
    private static final String APP_TITLE = "GPXLab Launcher";
    private final List<LauncherCommand> commands = List.of(
            new LauncherCommand("Sanitize GPX", "Remove selected information from a GPX file.", SanitizeGPX::display),
            new LauncherCommand("GPX Simplifier", "Simplify GPX tracks while preserving their centerline.", SimplifyGPX::display));

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        GPXLab.readConfig();
        initLogging(GPXLab.CONFIG);
        openVault(GPXLab.CONFIG);
    }

    static void openVault(Config config) {
        String vaultFile = config.getString(VAULT_FILE_TAG);
        if (StringUtil.isBlank(vaultFile)) {
            vaultFile = GPXLab.DEFAULT_VAULT_FILE;
            config.set(VAULT_FILE_TAG, vaultFile);
        }
        String password = System.getProperty("vault.password");
        if (StringUtil.isBlank(password)) {
            throw new IllegalStateException("The `vault.password` system property is not defined.");
        }
        GPXLab.loadVault(vaultFile, password);
    }

    public static void initLogging(Config config) {
        Log.initDefaultLogging();
        if (config == null) return;
        Object logger = config.get(LOGGER_TAG);
        if (logger == null) logger = config.get("logger");
        if (logger == null) {
            Config section = new Config();
            Config defaults = new Config();
            defaults.setArrayBias(true);
            defaults.add(Log.INFO);
            defaults.add(Log.NOTICE);
            defaults.add(Log.WARN);
            defaults.add(Log.ERROR);
            defaults.add(Log.FATAL);
            section.put(CATEGORIES_TAG, defaults);
            config.put(LOGGER_TAG, section);
            logger = section;
        }
        List<String> categories = extractCategories(logger);
        if (!categories.isEmpty()) {
            Log.stopLoggingAllCategories();
            for (String category : categories) Log.startLogging(category);
        }
    }

    public static List<String> extractCategories(Object logger) {
        List<String> categories = new ArrayList<>();
        if (logger instanceof DataFrame frame) {
            if (frame.isArray() || frame.isArrayBiased()) {
                addFrame(categories, frame);
            } else {
                DataField field = frame.getFieldIgnoreCase(CATEGORIES_TAG);
                if (field == null) field = frame.getFieldIgnoreCase("category");
                if (field != null) addValues(categories, field.getObjectValue());
                for (int i = 0; i < frame.getFieldCount(); i++) {
                    DataField item = frame.getField(i);
                    if (item.getName() == null || item.getName().trim().isEmpty()) {
                        addValues(categories, item.getObjectValue());
                    }
                }
            }
        } else {
            addValues(categories, logger);
        }
        return categories;
    }

    private static void addFrame(List<String> categories, DataFrame frame) {
        for (int i = 0; i < frame.getFieldCount(); i++) {
            addValues(categories, frame.getField(i).getObjectValue());
        }
    }

    private static void addValues(List<String> categories, Object value) {
        if (value instanceof DataFrame frame) {
            addFrame(categories, frame);
        } else if (value instanceof Object[] values) {
            for (Object item : values) addValues(categories, item);
        } else if (value != null && StringUtil.isNotBlank(value.toString())) {
            for (String token : value.toString().split("[,:;|\\s]+")) {
                if (StringUtil.isNotBlank(token)) categories.add(token.trim());
            }
        }
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle(ROOT_STYLE);
        root.setTop(createMenuBar());
        VBox content = new VBox(15);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.TOP_CENTER);
        Label title = new Label(APP_TITLE);
        title.setStyle(LABEL_STYLE);
        VBox.setMargin(title, new Insets(0, 0, 20, 0));
        content.getChildren().addAll(title, createButtonGrid(commands));
        root.setCenter(content);
        stage.setTitle(APP_TITLE);
        stage.setScene(new Scene(root, 600, 700));
        stage.show();
    }

    MenuBar createMenuBar() {
        MenuBar bar = new MenuBar();
        bar.setStyle(MENU_STYLE);
        Menu file = new Menu("File");
        MenuItem settings = new MenuItem("Settings");
        settings.setOnAction(event -> openSettings());
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(event -> Platform.exit());
        file.getItems().addAll(settings, new SeparatorMenuItem(), exit);
        bar.getMenus().add(file);
        return bar;
    }

    private void openSettings() {
        ConfigEditor editor = new ConfigEditor(GPXLab.CONFIG);
        editor.setOnApply(LauncherApp::applyConfig);
        Config result = editor.showAndWaitResult();
        if (result != null) applyConfig(result);
    }

    private static void applyConfig(Config config) {
        GPXLab.CONFIG = config;
        initLogging(config);
    }

    GridPane createButtonGrid(List<LauncherCommand> commandList) {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setAlignment(Pos.TOP_CENTER);
        grid.setMaxWidth(Double.MAX_VALUE);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        if (commandList != null) {
            for (int i = 0; i < commandList.size(); i++) {
                Button button = createCommandButton(commandList.get(i));
                GridPane.setHgrow(button, Priority.ALWAYS);
                GridPane.setFillWidth(button, true);
                grid.add(button, i % 2, i / 2);
            }
        }
        return grid;
    }

    Button createCommandButton(LauncherCommand command) {
        Button button = new Button(command.name());
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle(BUTTON_STYLE);
        if (StringUtil.isNotBlank(command.description())) button.setTooltip(new Tooltip(command.description()));
        button.setOnMouseEntered(event -> button.setStyle(BUTTON_HOVER_STYLE));
        button.setOnMouseExited(event -> button.setStyle(BUTTON_STYLE));
        button.setOnAction(event -> command.action().run());
        return button;
    }

    @Override
    public void stop() {
        GPXLab.saveConfig();
    }
}