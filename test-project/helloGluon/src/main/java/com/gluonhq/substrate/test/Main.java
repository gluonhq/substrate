package com.gluonhq.substrate.test;

import com.gluonhq.attach.display.DisplayService;
import com.gluonhq.attach.util.Platform;
import com.gluonhq.charm.glisten.application.MobileApplication;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.control.FloatingActionButton;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import com.gluonhq.charm.glisten.visual.Swatch;
import javafx.geometry.Dimension2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class Main extends MobileApplication {

    @Override
    public void init() {
        addViewFactory(HOME_VIEW, () -> {
            FloatingActionButton fab = new FloatingActionButton(MaterialDesignIcon.SEARCH.text,
                    e -> System.out.println("Search"));

            ImageView imageView = new ImageView(new Image(Main.class.getResourceAsStream("openduke.png")));

            imageView.setFitHeight(200);
            imageView.setPreserveRatio(true);

            Label label = new Label("Hello, Gluon Mobile!");
            VBox root = new VBox(20, imageView, label);
            root.setAlignment(Pos.CENTER);

            View view = new View(root) {
                @Override
                protected void updateAppBar(AppBar appBar) {
                    appBar.setTitleText("Gluon Mobile");
                }
            };

            fab.showOn(view);

            return view;
        });
    }

    @Override
    public void postInit(Scene scene) {
        Swatch.LIGHT_GREEN.assignTo(scene);
        scene.getStylesheets().add(Main.class.getResource("styles.css").toExternalForm());

        if (Platform.isDesktop()) {
            Dimension2D dimension2D = DisplayService.create()
                    .map(DisplayService::getDefaultDimensions)
                    .orElse(new Dimension2D(640, 480));
            scene.getWindow().setWidth(dimension2D.getWidth());
            scene.getWindow().setHeight(dimension2D.getHeight());
        }

        PauseTransition pause = new PauseTransition(Duration.seconds(5));
        pause.setOnFinished(f -> System.exit(0));
        pause.play();
    }

    public static void main(String[] args) {
        System.setProperty("prism.verbose", "true");
        launch(args);
    }

}