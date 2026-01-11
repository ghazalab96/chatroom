package at.ac.hcw.chat.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;

public class ChatController {
    // UI Elements for Login Window
    @FXML private TextField ipField, portField, nameField;
    @FXML private Label statusLabel;

    // UI Elements for Chat Window
    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private Label welcomeLabel;

    // Networking (Static to persist across scene changes)
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String userName;
    private static volatile boolean isRunning = false;

    // Reference to the currently displayed controller instance
    private static ChatController activeController;

    @FXML
    public void initialize() {
        // Update the active controller reference whenever a new FXML is loaded
        activeController = this;
    }

    /**
     * Logic for the "Connect" button in Login View
     */
    @FXML
    protected void onConnectButtonClick() {
        userName = nameField.getText();
        if (userName.isEmpty()) {
            statusLabel.setText("Please enter your name!");
            return;
        }

        try {
            String ip = ipField.getText();
            int port = Integer.parseInt(portField.getText());

            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            isRunning = true;

            // Start the message listener thread
            new Thread(this::listenToServer).start();

            // Transition to the Chat Room scene
            switchScene("/at/ac/hcw/chat/client/chat-view.fxml");

        } catch (Exception e) {
            statusLabel.setText("Connection failed: " + e.getMessage());
        }
    }

    /**
     * This method runs in a separate thread on the Client.
     * It listens for messages coming FROM the server.
     */
    private void listenToServer() {
        try {
            String line;
            while (isRunning) {
                line = in.readLine();

                // If server shuts down cleanly, it returns null
                if (line == null) {
                    break;
                }

                String finalLine = line;
                Platform.runLater(() -> {
                    if (activeController != null && activeController.chatArea != null) {
                        activeController.chatArea.appendText(finalLine + "\n");
                    }
                });
            }
        } catch (IOException e) {
            // This happens when server is forcefully stopped (Connection Reset)
            System.out.println("Connection lost: " + e.getMessage());
        } finally {
            // Trigger the popup only if the user didn't click "Disconnect" manually
            if (isRunning) {
                Platform.runLater(this::showErrorPopup);
            }
        }
    }

    /**
     * Creates and displays a modal pop-up window when the server goes down
     */

    /**
     * Closes the Chat Window and displays the Error Window
     */
    private void showErrorPopup() {
        try {
            isRunning = false; // Stop the chat loop
            if (socket != null) socket.close();

            // 1. Find the current Chat Stage and CLOSE it
            if (activeController != null && activeController.chatArea != null) {
                Stage chatStage = (Stage) activeController.chatArea.getScene().getWindow();
                chatStage.close();
            }

            // 2. Load and Show the Error Window as a new independent stage
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/ac/hcw/chat/client/error-view.fxml"));
            Parent root = loader.load();

            Stage errorStage = new Stage();
            errorStage.setTitle("Connection Error");
            errorStage.setScene(new Scene(root));
            errorStage.setResizable(false);
            errorStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Handles sending messages to the server
     */
    @FXML
    protected void onSendButtonClick() {
        String msg = messageField.getText();
        if (!msg.isEmpty() && out != null) {
            out.println(userName + ": " + msg);
            messageField.clear();
        }
    }

    /**
     * Handles the button click in both Chat and Error windows to return to Login
     */
    @FXML
    protected void onDisconnectButtonClick() {
        isRunning = false;
        try {
            if (socket != null) socket.close();

            // Find the stage that triggered the event (either Chat or Error stage)
            Stage currentStage;
            if (ipField != null) {
                // We are already in the Login or Error window (depending on fields)
                currentStage = (Stage) ipField.getScene().getWindow();
            } else if (chatArea != null) {
                // We are in the Chat window
                currentStage = (Stage) chatArea.getScene().getWindow();
            } else {
                // Fallback to find the focused window (the Error window)
                currentStage = (Stage) Stage.getWindows().filtered(w -> w.isFocused()).get(0);
            }

            // Switch the current window back to the Login view
            switchSceneOnStage(currentStage, "/at/ac/hcw/chat/client/login-view.fxml");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Helper to switch scenes on a specific stage
     */
    private void switchSceneOnStage(Stage stage, String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();
        stage.setScene(new Scene(root));

        if (fxmlPath.contains("login")) {
            stage.setTitle("HCW Chat - Login");
        } else {
            stage.setTitle("HCW Chat - Room");
            ChatController nextController = loader.getController();
            nextController.welcomeLabel.setText("Chatting as: " + userName);
        }
    }

    /**
     * Standard scene switcher for the current window
     */
    private void switchScene(String fxmlPath) {
        try {
            Stage stage;
            if (ipField != null) stage = (Stage) ipField.getScene().getWindow();
            else stage = (Stage) chatArea.getScene().getWindow();

            switchSceneOnStage(stage, fxmlPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}