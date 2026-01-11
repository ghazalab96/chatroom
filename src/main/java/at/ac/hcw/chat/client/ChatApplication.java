package at.ac.hcw.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main Application class for HCW Chat Client
 * Package: at.ac.hcw.chat.client
 */
public class ChatApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Path adjusted to the 'client' package structure
        FXMLLoader fxmlLoader = new FXMLLoader(ChatApplication.class.getResource("/at/ac/hcw/chat/client/chat-view.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 600, 450);

        stage.setTitle("HCW Chat Room");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        // Set network preference for better compatibility
        System.setProperty("java.net.preferIPv4Stack", "true");
        launch();
    }
}