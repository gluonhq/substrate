package com.gluonhq.substrate.test;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.util.ResourceBundle;

public class MainController {

    @FXML
    private Button button;

    @FXML
    private Label label;

    @FXML
    private ResourceBundle resources;

    public void initialize() {
        button.setOnAction(e -> {
            label.setText(resources.getString("label.text") + " " + System.getProperty("javafx.version"));
            label.setVisible(!label.isVisible());
        });
    }

}