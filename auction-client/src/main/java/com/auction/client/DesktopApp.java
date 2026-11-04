package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX Hello World: preparation for the MVC screens in later weeks. */
public final class DesktopApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                DesktopApp.class.getResource("/com/auction/client/login.fxml"));
        stage.setTitle("Online Auction System");
        stage.setScene(new Scene(loader.load(), 700, 420));
        stage.setMinWidth(600);
        stage.setMinHeight(360);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
