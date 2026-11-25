package com.sdcote.gpxlab;

import javafx.scene.control.Alert;

public class HelloWorld {
    public static String execute() {
        return "hello World!";
    }

    public static void display() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Hello World");
        alert.setHeaderText(null);
        alert.setContentText(execute());
        alert.showAndWait();
    }
}