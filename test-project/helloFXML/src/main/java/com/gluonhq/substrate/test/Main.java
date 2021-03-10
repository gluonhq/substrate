package com.gluonhq.substrate.test;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.layout.AnchorPane;
import javafx.fxml.FXMLLoader;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.Locale;
import java.io.IOException;
import java.util.ResourceBundle;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        AnchorPane root = FXMLLoader.load(Main.class.getResource("main.fxml"),
                ResourceBundle.getBundle("com.gluonhq.substrate.test.bundle"));

        String osName  = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        Scene scene;
        if (osName.contains("mac") || osName.contains("nux")) {
            scene = new Scene(root, 600, 400);
        } else {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            scene = new Scene(root, bounds.getWidth(), bounds.getHeight());
        }
        stage.setScene(scene);
        stage.show();

        if (System.getProperty("javafx.platform") == null) {
            PauseTransition pause = new PauseTransition(Duration.seconds(5));
            pause.setOnFinished(f -> System.exit(0));
            pause.play();
        }
    }

    public static void main(String[] args) {
        System.setProperty("prism.verbose", "true");
        launch(args);
    }

}