package at.ac.hcw.chat.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller with Silent Private Tab creation and Red Dot notifications.
 */
public class ChatController {
    @FXML private TextField ipField, portField, nameField, messageField;
    @FXML private Label statusLabel, welcomeLabel;
    @FXML private TextArea chatArea;
    @FXML private ListView<String> userListView;
    @FXML private TabPane chatTabPane;

    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String userName;
    private static volatile boolean isRunning = false;
    private static ChatController activeController;

    private static final Map<String, TextArea> privateChatLog = new HashMap<>();
    private static final Map<String, Tab> tabMap = new HashMap<>();

    @FXML
    public void initialize() {
        activeController = this;

        // Double-click to open and JUMP to private chat
        if (userListView != null) {
            userListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String selectedUser = userListView.getSelectionModel().getSelectedItem();
                    if (selectedUser != null && !selectedUser.equals(userName)) {
                        openPrivateTab(selectedUser, true); // true = switch to this tab
                    }
                }
            });
        }

        // Clear notification dot when user manually clicks a tab
        if (chatTabPane != null) {
            chatTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null) newTab.setGraphic(null);
            });
        }
    }

    private Circle createNotificationDot() {
        Circle dot = new Circle(5);
        dot.setStyle("-fx-fill: #FF5252; -fx-stroke: white; -fx-stroke-width: 1;");
        return dot;
    }

    /**
     * @param targetUser The name of the user.
     * @param shouldFocus If true, the app will switch to this tab. If false, it stays silent.
     */
    private void openPrivateTab(String targetUser, boolean shouldFocus) {
        Platform.runLater(() -> {
            if (activeController == null || activeController.chatTabPane == null) return;

            Tab targetTab = tabMap.get(targetUser);

            // If tab doesn't exist, create it
            if (targetTab == null) {
                TextArea newLog = new TextArea();
                newLog.setEditable(false);
                newLog.setWrapText(true);
                newLog.setStyle("-fx-background-radius: 0 0 15 15; -fx-border-radius: 0 0 15 15;");

                targetTab = new Tab(targetUser, newLog);
                targetTab.setClosable(true);
                targetTab.setOnClosed(e -> {
                    privateChatLog.remove(targetUser);
                    tabMap.remove(targetUser);
                });

                activeController.chatTabPane.getTabs().add(targetTab);
                privateChatLog.put(targetUser, newLog);
                tabMap.put(targetUser, targetTab);
            }

            // Only switch view if shouldFocus is true (e.g. from double-click)
            if (shouldFocus) {
                activeController.chatTabPane.getSelectionModel().select(targetTab);
                targetTab.setGraphic(null);
            }
        });
    }

    private void handleMessageRouting(String msg) {
        if (activeController == null) return;

        if (msg.startsWith("USERLIST:")) {
            String[] users = msg.substring(9).split(",");
            activeController.userListView.getItems().clear();
            for (String u : users) if (!u.isEmpty()) activeController.userListView.getItems().add(u);
        }
        else if (msg.startsWith("[Private from ")) {
            String sender = msg.substring(14, msg.indexOf("]:"));

            // 1. Ensure tab exists but DO NOT jump to it
            openPrivateTab(sender, false);

            Platform.runLater(() -> {
                TextArea log = privateChatLog.get(sender);
                if (log != null) log.appendText(getTimestamp() + msg + "\n");

                // 2. Show red dot if we are NOT currently looking at that tab
                Tab tab = tabMap.get(sender);
                if (tab != null && !activeController.chatTabPane.getSelectionModel().getSelectedItem().equals(tab)) {
                    tab.setGraphic(createNotificationDot());
                }
            });
        }
        else if (msg.startsWith("[Private to ")) {
            String target = msg.substring(12, msg.indexOf("]:"));
            openPrivateTab(target, false);
            Platform.runLater(() -> privateChatLog.get(target).appendText(getTimestamp() + msg + "\n"));
        }
        else {
            // General Chat
            if (activeController.chatArea != null) {
                activeController.chatArea.appendText(getTimestamp() + msg + "\n");
                Tab generalTab = activeController.chatTabPane.getTabs().get(0);
                if (!activeController.chatTabPane.getSelectionModel().getSelectedItem().equals(generalTab)) {
                    generalTab.setGraphic(createNotificationDot());
                }
            }
        }
    }

    @FXML
    protected void onSendButtonClick() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty() || out == null) return;

        Tab selectedTab = chatTabPane.getSelectionModel().getSelectedItem();
        String tabName = selectedTab.getText();

        if (tabName.equals("General")) out.println(msg);
        else out.println("@" + tabName + ": " + msg);

        messageField.clear();
    }

    // --- Rest of the methods (listenToServer, getTimestamp, showErrorPopup, etc.) stay the same ---

    private String getTimestamp() {
        return "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "] ";
    }

    private void listenToServer() {
        try {
            String line;
            while (isRunning && (line = in.readLine()) != null) {
                final String msg = line;
                Platform.runLater(() -> handleMessageRouting(msg));
            }
        } catch (IOException e) {
            // Error handling
        } finally {
            if (isRunning) Platform.runLater(this::showErrorPopup);
        }
    }

    @FXML
    protected void onConnectButtonClick() {
        userName = nameField.getText().trim();
        if (userName.isEmpty()) return;
        try {
            socket = new Socket(ipField.getText(), Integer.parseInt(portField.getText()));
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(userName);
            isRunning = true;
            new Thread(this::listenToServer).start();
            switchScene("/at/ac/hcw/chat/client/chat-view.fxml");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showErrorPopup() {
        try {
            isRunning = false;
            if (socket != null) socket.close();
            if (activeController != null && activeController.chatTabPane != null) {
                ((Stage) activeController.chatTabPane.getScene().getWindow()).close();
            }
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/ac/hcw/chat/client/error-view.fxml"));
            Stage errorStage = new Stage();
            errorStage.setScene(new Scene(loader.load()));
            errorStage.setTitle("Connection Error");
            errorStage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    protected void onDisconnectButtonClick() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
            privateChatLog.clear();
            tabMap.clear();
            Stage currentStage = (Stage) (ipField != null ? ipField.getScene().getWindow() : chatTabPane.getScene().getWindow());
            switchSceneOnStage(currentStage, "/at/ac/hcw/chat/client/login-view.fxml");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void switchSceneOnStage(Stage stage, String path) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
        Parent root = loader.load();
        stage.setScene(new Scene(root));
        if (path.contains("login")) stage.setTitle("HCW Login");
        else {
            stage.setTitle("HCW Room");
            ((ChatController)loader.getController()).welcomeLabel.setText("User: " + userName);
        }
    }

    private void switchScene(String path) {
        try {
            Stage stage = (Stage) (ipField != null ? ipField.getScene().getWindow() : chatTabPane.getScene().getWindow());
            switchSceneOnStage(stage, path);
        } catch (Exception e) { e.printStackTrace(); }
    }
}