package at.ac.hcw.chat.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.io.*;
import java.net.Socket;

/**
 * Controller for the Chat Client UI.
 * Handles networking logic, UI updates, and auto-reconnection.
 */
public class ChatController {
    // UI Elements defined in FXML
    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private TextField nameField;
    @FXML private TextField messageField;
    @FXML private TextArea chatArea;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Button sendButton;

    // Networking objects
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;

    // Flag to control the listening thread and reconnection logic
    private volatile boolean isRunning = false;

    /**
     * Action for the Connect button.
     */
    @FXML
    protected void onConnectButtonClick() {
        startConnection();
    }

    /**
     * Establishes a connection to the server.
     */
    private void startConnection() {
        String ip = ipField.getText();
        String portStr = portField.getText();

        try {
            int port = Integer.parseInt(portStr);
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            isRunning = true;
            updateUI(true);
            chatArea.appendText("[System]: Connected to HCW Server.\n");

            // Start a dedicated thread to receive messages from the server
            new Thread(this::listenToServer).start();

        } catch (NumberFormatException e) {
            chatArea.appendText("[Error]: Invalid port number.\n");
        } catch (IOException e) {
            chatArea.appendText("[System]: Server not found. Retrying in 3 seconds...\n");
            attemptReconnect();
        }
    }

    /**
     * Continuously listens for incoming messages from the server.
     */
    private void listenToServer() {
        try {
            String line;
            // Loop until the stream ends or isRunning is set to false
            while (isRunning && (line = in.readLine()) != null) {
                String finalLine = line;
                // Update UI on the JavaFX Application Thread
                Platform.runLater(() -> chatArea.appendText(finalLine + "\n"));
            }
        } catch (IOException e) {
            // If the connection drops unexpectedly while isRunning is true
            if (isRunning) {
                Platform.runLater(() -> chatArea.appendText("[Alert]: Server went down!\n"));
                attemptReconnect();
            }
        }
    }

    /**
     * Background thread that attempts to reconnect to the server every 3 seconds.
     */
    private void attemptReconnect() {
        updateUI(false);
        new Thread(() -> {
            // Keep trying while isRunning is true and socket is closed/null
            while (isRunning && (socket == null || socket.isClosed())) {
                try {
                    Thread.sleep(3000); // Wait 3 seconds before next attempt

                    String ip = ipField.getText();
                    int port = Integer.parseInt(portField.getText());
                    socket = new Socket(ip, port);

                    // Reconnection successful
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    Platform.runLater(() -> {
                        chatArea.appendText("[Success]: Server back online!\n");
                        updateUI(true);
                    });

                    // Restart the listener thread
                    new Thread(this::listenToServer).start();
                    break;
                } catch (Exception e) {
                    // Reconnection failed, loop continues
                }
            }
        }).start();
    }

    /**
     * Action for the Disconnect button.
     */
    @FXML
    protected void onDisconnectButtonClick() {
        isRunning = false; // Prevents auto-reconnect
        closeConnection();
        chatArea.appendText("[System]: You disconnected.\n");
        updateUI(false);
    }

    /**
     * Closes networking resources safely.
     */
    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
            socket = null; // Important for the reconnect logic check
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Action for the Send button or Enter key.
     */
    @FXML
    protected void onSendButtonClick() {
        String msg = messageField.getText();
        String name = nameField.getText();

        if (!msg.isEmpty() && out != null) {
            out.println(name + ": " + msg);
            messageField.clear();
        }
    }

    /**
     * Toggles UI elements based on connection status.
     * @param connected True if connected, false otherwise.
     */
    private void updateUI(boolean connected) {
        Platform.runLater(() -> {
            connectButton.setDisable(connected);
            disconnectButton.setDisable(!connected);
            sendButton.setDisable(!connected);
            messageField.setDisable(!connected);

            // Allow editing settings only when disconnected
            ipField.setDisable(connected);
            portField.setDisable(connected);
            nameField.setDisable(connected);
        });
    }
}