package at.ac.hcw.chat.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Main Controller for the Chat Application.
 * Package: at.ac.hcw.chat.client
 */
public class ChatController {
    // UI Elements for Login Window
    @FXML private TextField ipField, portField, nameField;
    @FXML private Label statusLabel;

    // UI Elements for Chat Window
    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private Label welcomeLabel;
    @FXML private ListView<String> userListView;

    // Networking Static Variables (Static to survive scene changes)
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String userName;
    private static volatile boolean isRunning = false;

    // Static reference to the currently active controller (for UI updates from the background thread)
    private static ChatController activeController;

    @FXML
    public void initialize() {
        // Set this instance as the active controller whenever an FXML is loaded
        activeController = this;

        // Auto-whisper: When a name in the list is clicked, prepare a private message
        if (userListView != null) {
            userListView.setOnMouseClicked(event -> {
                String selectedUser = userListView.getSelectionModel().getSelectedItem();
                if (selectedUser != null && !selectedUser.equals(userName)) {
                    messageField.setText("@" + selectedUser + ": ");
                    messageField.requestFocus();
                    messageField.positionCaret(messageField.getText().length());
                }
            });
        }
    }

    /**
     * Logic for the Connect button in the Login View.
     */
    @FXML
    protected void onConnectButtonClick() {
        userName = nameField.getText().trim();
        if (userName.isEmpty()) {
            statusLabel.setText("Please enter your name!");
            return;
        }

        try {
            String ip = ipField.getText();
            int port = Integer.parseInt(portField.getText());

            // 1. Establish connection
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 2. Protocol: Send the Username immediately as the first message
            out.println(userName);

            isRunning = true;

            // 3. Start the background thread to listen for server messages
            new Thread(this::listenToServer).start();

            // 4. Switch to the main Chat Room scene
            switchScene("/at/ac/hcw/chat/client/chat-view.fxml");

        } catch (Exception e) {
            statusLabel.setText("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Background thread that processes all incoming messages from the server.
     */
    private void listenToServer() {
        try {
            String line;
            while (isRunning) {
                line = in.readLine();

                // If server goes down, readLine returns null
                if (line == null) {
                    break;
                }

                // Handle protocol for updating the Online User List
                if (line.startsWith("USERLIST:")) {
                    String listData = line.substring(9);
                    String[] users = listData.split(",");

                    Platform.runLater(() -> {
                        if (activeController != null && activeController.userListView != null) {
                            activeController.userListView.getItems().clear();
                            activeController.userListView.getItems().addAll(users);
                        }
                    });
                } else {
                    // Handle standard chat messages (Public or Private)
                    String finalLine = line;
                    Platform.runLater(() -> {
                        if (activeController != null && activeController.chatArea != null) {
                            activeController.chatArea.appendText(finalLine + "\n");
                        }
                    });
                }
            }
        } catch (IOException e) {
            // Log connection loss
            System.out.println("Connection lost to server.");
        } finally {
            // If the user didn't manually disconnect, show the Error Popup
            if (isRunning) {
                Platform.runLater(this::showErrorPopup);
            }
        }
    }

    /**
     * Closes the Chat stage and opens the independent Error Window.
     */
    private void showErrorPopup() {
        try {
            isRunning = false;
            if (socket != null) socket.close();

            // Find and close the Chat Stage
            if (activeController != null && activeController.chatArea != null) {
                Stage chatStage = (Stage) activeController.chatArea.getScene().getWindow();
                chatStage.close();
            }

            // Open the independent Error Stage
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
     * Sends the text from the messageField to the server.
     */
    @FXML
    protected void onSendButtonClick() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty() && out != null) {
            out.println(msg);
            messageField.clear();
        }
    }

    /**
     * Handles Disconnect requests from either the Chat Room or the Error Window.
     */
    @FXML
    protected void onDisconnectButtonClick() {
        isRunning = false;
        try {
            if (socket != null) socket.close();

            // Determine which stage called the disconnect
            Stage currentStage;
            if (ipField != null) {
                currentStage = (Stage) ipField.getScene().getWindow();
            } else if (chatArea != null) {
                currentStage = (Stage) chatArea.getScene().getWindow();
            } else {
                // Find the currently focused window (the Error popup)
                List<Window> windows = Window.getWindows().filtered(Window::isFocused);
                currentStage = windows.isEmpty() ? (Stage) Window.getWindows().get(0) : (Stage) windows.get(0);
            }

            // Change the current window content back to login-view
            switchSceneOnStage(currentStage, "/at/ac/hcw/chat/client/login-view.fxml");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to switch FXML content on a specific Stage.
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
     * General scene switcher for the current window.
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