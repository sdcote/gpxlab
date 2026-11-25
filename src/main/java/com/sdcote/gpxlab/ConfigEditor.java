package com.sdcote.gpxlab;

import coyote.commons.StringUtil;
import coyote.commons.cfg.Config;
import coyote.commons.dataframe.DataField;
import coyote.commons.dataframe.DataFrame;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.DefaultStringConverter;

import java.util.Optional;
import java.util.function.Consumer;

/** A configuration editor for properties, sections and arrays. */
public class ConfigEditor extends Stage {
    public static final String TITLE_DIALOG = "Config Editor";
    private static final String STYLE_DARK_BACKGROUND = "-fx-background-color: #2b2b2b;";
    private static final String STYLE_TITLE = "-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_INSTRUCTIONS = "-fx-text-fill: #cccccc; -fx-font-size: 13px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_TREE_VIEW = "-fx-background-color: #313335; -fx-control-inner-background: #313335; -fx-control-inner-background-alt: #2b2b2b; -fx-border-color: #4c5052; -fx-border-width: 1px; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-text-fill: #ffffff;";
    private static final String STYLE_TABLE_VIEW = "-fx-background-color: #313335; -fx-control-inner-background: #313335; -fx-control-inner-background-alt: #2e3032; -fx-border-color: #4c5052; -fx-border-width: 1px; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-text-fill: #ffffff;";
    private static final String STYLE_SPLIT_PANE = "-fx-background-color: #2b2b2b; -fx-box-border: transparent; -fx-padding: 0;";
    private static final String STYLE_TEXT_FIELD = "-fx-background-color: #3c3f41; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-border-color: #555555; -fx-border-radius: 3px; -fx-background-radius: 3px; -fx-padding: 4px 6px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_BUTTON_DEFAULT = "-fx-background-color: #3c3f41; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-padding: 8px 18px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_BUTTON_HOVER = "-fx-background-color: #4c5052; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-padding: 8px 18px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_PRIMARY_BUTTON_DEFAULT = "-fx-background-color: #365880; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_PRIMARY_BUTTON_HOVER = "-fx-background-color: #436d9d; -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-font-family: 'Segoe UI', sans-serif;";
    private static final String STYLE_CONTEXT_MENU = "-fx-background-color: #3c3f41; -fx-border-color: #555555; -fx-border-width: 1px; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px;";
    private static final String STYLE_CELL = "-fx-text-fill: #ffffff; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px;";
    private static final String STYLE_DIALOG_PANE = "-fx-background-color: #2b2b2b; -fx-border-color: #4c5052; -fx-border-width: 1px; -fx-font-family: 'Segoe UI', sans-serif;";

    private ConfigModel rootModel;
    private TreeView<ConfigModel> treeView;
    private TableView<ConfigAttribute> tableView;
    private Button btnApply;
    private Button btnOk;
    private Button btnCancel;
    private Config resultConfig;
    private Consumer<Config> onApply;

    public ConfigEditor(Config config) {
        initModality(Modality.APPLICATION_MODAL);
        setTitle(TITLE_DIALOG);
        buildUI();
        loadConfig(config != null ? config : new Config());
    }

    public void setOnApply(Consumer<Config> onApply) { this.onApply = onApply; }
    public Config showAndWaitResult() { showAndWait(); return resultConfig; }
    public TreeView<ConfigModel> getTreeView() { return treeView; }
    public TableView<ConfigAttribute> getTableView() { return tableView; }
    public Button getApplyButton() { return btnApply; }
    public Button getOkButton() { return btnOk; }
    public Button getCancelButton() { return btnCancel; }
    public Config getResultConfig() { return resultConfig; }
    public ConfigModel getRootModel() { return rootModel; }

    private void buildUI() {
        BorderPane root = new BorderPane();
        root.setStyle(STYLE_DARK_BACKGROUND);
        VBox header = new VBox(6);
        header.setPadding(new Insets(15, 20, 10, 20));
        header.setStyle(STYLE_DARK_BACKGROUND);
        Label title = new Label("Configuration Settings");
        title.setStyle(STYLE_TITLE);
        Label instructions = new Label("View and edit configuration sections, arrays, and properties:");
        instructions.setStyle(STYLE_INSTRUCTIONS);
        header.getChildren().addAll(title, instructions);
        root.setTop(header);

        treeView = new TreeView<>();
        treeView.setPrefWidth(250);
        treeView.setStyle(STYLE_TREE_VIEW);
        treeView.setCellFactory(view -> new TreeCell<ConfigModel>() {
            @Override protected void updateItem(ConfigModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                if (empty || item == null) setGraphic(null);
                setStyle(STYLE_CELL);
            }
        });
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) ->
                tableView.setItems(selected == null ? FXCollections.observableArrayList() : selected.getValue().getAttributes()));
        setupTreeContextMenu();

        tableView = new TableView<>();
        tableView.setEditable(true);
        tableView.setStyle(STYLE_TABLE_VIEW);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<ConfigAttribute, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(data -> data.getValue().nameProperty());
        name.setCellFactory(column -> createEditingCell());
        name.setOnEditCommit(event -> { event.getRowValue().setName(event.getNewValue()); checkAndEnsureEmptyRow(); });
        TableColumn<ConfigAttribute, String> value = new TableColumn<>("Value");
        value.setCellValueFactory(data -> data.getValue().valueProperty());
        value.setCellFactory(column -> createEditingCell());
        value.setOnEditCommit(event -> { event.getRowValue().setValue(event.getNewValue()); checkAndEnsureEmptyRow(); });
        tableView.getColumns().add(name);
        tableView.getColumns().add(value);
        setupTableContextMenu();

        SplitPane split = new SplitPane();
        split.setStyle(STYLE_SPLIT_PANE);
        split.getItems().addAll(treeView, tableView);
        split.setDividerPositions(0.3);
        VBox center = new VBox(split);
        center.setPadding(new Insets(0, 20, 0, 20));
        center.setStyle(STYLE_DARK_BACKGROUND);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setCenter(center);
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(15, 20, 15, 20));
        buttons.setStyle(STYLE_DARK_BACKGROUND);
        btnApply = createButton("Apply", this::handleApply, false);
        btnOk = createButton("OK", this::handleOk, true);
        btnCancel = createButton("Cancel", this::handleCancel, false);
        buttons.getChildren().addAll(btnApply, btnOk, btnCancel);
        root.setBottom(buttons);
        setScene(new Scene(root, 800, 600));
    }

    private TextFieldTableCell<ConfigAttribute, String> createEditingCell() {
        return new TextFieldTableCell<ConfigAttribute, String>(new DefaultStringConverter()) {
            private boolean listenerAdded;
            @Override public void startEdit() {
                super.startEdit();
                if (isEditing() && getGraphic() instanceof TextField) {
                    TextField field = (TextField) getGraphic();
                    field.setStyle(STYLE_TEXT_FIELD);
                    if (!listenerAdded) {
                        field.focusedProperty().addListener((obs, wasFocused, focused) -> {
                            if (!focused && isEditing()) commitEdit(field.getText());
                        });
                        listenerAdded = true;
                    }
                }
            }
            @Override public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(STYLE_CELL);
            }
        };
    }

    private void setupTreeContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.setStyle(STYLE_CONTEXT_MENU);
        MenuItem section = new MenuItem("Add Section");
        section.setOnAction(event -> showAddSectionDialog(false));
        MenuItem array = new MenuItem("Add Array");
        array.setOnAction(event -> showAddSectionDialog(true));
        MenuItem delete = new MenuItem("Delete Section");
        delete.setOnAction(event -> deleteSelectedSection());
        menu.getItems().addAll(section, array, new SeparatorMenuItem(), delete);
        treeView.setContextMenu(menu);
    }

    private void setupTableContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.setStyle(STYLE_CONTEXT_MENU);
        MenuItem add = new MenuItem("Add Row");
        add.setOnAction(event -> {
            ConfigModel selected = getSelectedModel();
            if (selected != null) selected.getAttributes().add(new ConfigAttribute("", ""));
        });
        MenuItem delete = new MenuItem("Delete Row");
        delete.setOnAction(event -> deleteSelectedRow());
        menu.getItems().addAll(add, delete);
        tableView.setContextMenu(menu);
        tableView.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.DELETE) deleteSelectedRow(); });
    }

    private void showAddSectionDialog(boolean isArray) {
        TreeItem<ConfigModel> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null) selected = treeView.getRoot();
        if (selected == null) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(isArray ? "Add Array" : "Add Section");
        dialog.setHeaderText(null);
        dialog.setContentText(isArray ? "Array Name:" : "Section Name:");
        dialog.initOwner(this);
        dialog.getDialogPane().setStyle(STYLE_DIALOG_PANE);
        dialog.getEditor().setStyle(STYLE_TEXT_FIELD);
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && StringUtil.isNotBlank(result.get())) {
            ConfigModel model = new ConfigModel(result.get().trim(), isArray);
            model.getAttributes().add(new ConfigAttribute("", ""));
            selected.getValue().getSections().add(model);
            TreeItem<ConfigModel> item = new TreeItem<>(model);
            selected.getChildren().add(item);
            selected.setExpanded(true);
            treeView.getSelectionModel().select(item);
        }
    }

    private void deleteSelectedSection() {
        TreeItem<ConfigModel> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getParent() != null) {
            TreeItem<ConfigModel> parent = selected.getParent();
            parent.getValue().getSections().remove(selected.getValue());
            parent.getChildren().remove(selected);
            treeView.getSelectionModel().select(parent);
        }
    }

    private void deleteSelectedRow() {
        ConfigModel selected = getSelectedModel();
        if (selected == null) return;
        ConfigAttribute attribute = tableView.getSelectionModel().getSelectedItem();
        if (attribute != null) {
            selected.getAttributes().remove(attribute);
            checkAndEnsureEmptyRow();
        }
    }

    private void checkAndEnsureEmptyRow() {
        ConfigModel selected = getSelectedModel();
        if (selected == null) return;
        selected.getAttributes().removeIf(ConfigAttribute::isBlank);
        selected.getAttributes().add(new ConfigAttribute("", ""));
    }

    private ConfigModel getSelectedModel() {
        TreeItem<ConfigModel> selected = treeView.getSelectionModel().getSelectedItem();
        return selected != null ? selected.getValue() : null;
    }

    private Button createButton(String text, Runnable action, boolean primary) {
        Button button = new Button(text);
        String normal = primary ? STYLE_PRIMARY_BUTTON_DEFAULT : STYLE_BUTTON_DEFAULT;
        String hover = primary ? STYLE_PRIMARY_BUTTON_HOVER : STYLE_BUTTON_HOVER;
        button.setStyle(normal);
        button.setOnMouseEntered(event -> button.setStyle(hover));
        button.setOnMouseExited(event -> button.setStyle(normal));
        button.setOnAction(event -> action.run());
        return button;
    }

    private void loadConfig(Config config) {
        rootModel = loadModel("Global", config);
        TreeItem<ConfigModel> root = buildTree(rootModel);
        treeView.setRoot(root);
        treeView.getSelectionModel().select(root);
    }

    private ConfigModel loadModel(String name, DataFrame frame) {
        ConfigModel model = new ConfigModel(name);
        if (frame != null && (frame.isArray() || frame.isArrayBiased())) model.setArray(true);
        if (frame != null) {
            for (int i = 0; i < frame.getFieldCount(); i++) {
                DataField field = frame.getField(i);
                Object value = field.getObjectValue();
                String fieldName = field.getName() == null ? "" : field.getName();
                if (value instanceof DataFrame) {
                    model.getSections().add(loadModel(fieldName, (DataFrame) value));
                } else if (field.isArray() || value instanceof Object[]) {
                    ConfigModel array = new ConfigModel(fieldName, true);
                    if (value instanceof Object[]) {
                        for (Object element : (Object[]) value) array.getAttributes().add(new ConfigAttribute("", element));
                    }
                    array.getAttributes().add(new ConfigAttribute("", ""));
                    model.getSections().add(array);
                } else {
                    model.getAttributes().add(new ConfigAttribute(fieldName, value));
                }
            }
        }
        model.getAttributes().add(new ConfigAttribute("", ""));
        return model;
    }

    private TreeItem<ConfigModel> buildTree(ConfigModel model) {
        TreeItem<ConfigModel> item = new TreeItem<>(model);
        item.setExpanded(true);
        for (ConfigModel section : model.getSections()) item.getChildren().add(buildTree(section));
        return item;
    }

    void handleOk() {
        cleanEmptyRows(rootModel);
        resultConfig = rootModel.toConfig();
        close();
    }

    void handleCancel() { resultConfig = null; close(); }

    void handleApply() {
        cleanEmptyRows(rootModel);
        if (onApply != null) onApply.accept(rootModel.toConfig());
        ensureEmptyRows(rootModel);
    }

    private void cleanEmptyRows(ConfigModel model) {
        model.getAttributes().removeIf(ConfigAttribute::isBlank);
        for (ConfigModel section : model.getSections()) cleanEmptyRows(section);
    }

    private void ensureEmptyRows(ConfigModel model) {
        if (model.getAttributes().stream().noneMatch(ConfigAttribute::isBlank)) model.getAttributes().add(new ConfigAttribute("", ""));
        for (ConfigModel section : model.getSections()) ensureEmptyRows(section);
    }

    public static class ConfigAttribute {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty value = new SimpleStringProperty();
        private final Object originalValue;

        public ConfigAttribute(String name, Object value) {
            this.name.set(name == null ? "" : name);
            this.originalValue = value;
            this.value.set(value == null ? "" : String.valueOf(value));
        }
        public StringProperty nameProperty() { return name; }
        public StringProperty valueProperty() { return value; }
        public String getName() { return name.get(); }
        public void setName(String name) { this.name.set(name); }
        public String getValue() { return value.get(); }
        public void setValue(String value) { this.value.set(value); }
        public boolean isBlank() {
            String n = getName();
            String v = getValue();
            return (n == null || n.trim().isEmpty()) && (v == null || v.trim().isEmpty());
        }
        public Object getOutputValue() {
            String current = value.get();
            return originalValue != null && current != null && current.equals(String.valueOf(originalValue)) ? originalValue : current;
        }
    }

    public static class ConfigModel {
        private String name;
        private boolean isArray;
        private final ObservableList<ConfigAttribute> attributes = FXCollections.observableArrayList();
        private final ObservableList<ConfigModel> sections = FXCollections.observableArrayList();

        public ConfigModel(String name) { this(name, false); }
        public ConfigModel(String name, boolean isArray) { this.name = name; this.isArray = isArray; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isArray() { return isArray; }
        public void setArray(boolean isArray) { this.isArray = isArray; }
        public ObservableList<ConfigAttribute> getAttributes() { return attributes; }
        public ObservableList<ConfigModel> getSections() { return sections; }

        public Config toConfig() {
            Config config = new Config();
            if (isArray) config.setArrayBias(true);
            for (ConfigAttribute attribute : attributes) {
                if (!attribute.isBlank()) {
                    String n = attribute.getName();
                    if (isArray || n == null || n.trim().isEmpty()) config.add(attribute.getOutputValue());
                    else config.put(n, attribute.getOutputValue());
                }
            }
            for (ConfigModel section : sections) {
                if (isArray) config.add(section.toConfig());
                else config.put(section.getName(), section.toConfig());
            }
            return config;
        }
        @Override public String toString() { return name != null && !name.isEmpty() ? name : (isArray ? "[]" : "{}"); }
    }
}