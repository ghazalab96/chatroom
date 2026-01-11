package at.ac.hcw.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ChatApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/at/ac/hcw/chat/client/login-view.fxml"));
        stage.setScene(new Scene(fxmlLoader.load()));
        stage.setTitle("HCW Chat - Login");
        stage.setResizable(false); // Login window looks better fixed
        stage.show();
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        launch();
    }
}