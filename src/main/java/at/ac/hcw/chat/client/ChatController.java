package at.ac.hcw.chat.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Window; // Crucial for finding the Error window stage
import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Final Controller with fix for Name Validation and Error-stage Navigation.
 * All previous features (Tabs, Notifications, Timestamps) are preserved.
 */
public class ChatController {
    // UI Elements for Login and Error views
    @FXML private TextField ipField, portField, nameField;
    @FXML private Label statusLabel;

    // UI Elements for Chat Room
    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private Label welcomeLabel;
    @FXML private ListView<String> userListView;
    @FXML private TabPane chatTabPane;

    // Networking Static Variables
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

        // Double-click detection for Private Chat
        if (userListView != null) {
            userListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String selectedUser = userListView.getSelectionModel().getSelectedItem();
                    if (selectedUser != null && !selectedUser.equals(userName)) {
                        openPrivateTab(selectedUser, true);
                    }
                }
            });
        }

        // Auto-remove notification dot when a tab is selected
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

    private void openPrivateTab(String targetUser, boolean shouldFocus) {
        Platform.runLater(() -> {
            if (activeController == null || activeController.chatTabPane == null) return;
            Tab targetTab = tabMap.get(targetUser);

            if (targetTab == null) {
                TextArea newLog = new TextArea();
                newLog.setEditable(false);
                newLog.setWrapText(true);
                newLog.setStyle("-fx-background-radius: 0 0 15 15;");

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
        // ۱. مدیریت پیام‌های خطای خصوصی (جدید)
        else if (msg.startsWith("[Private Error ")) {
            String target = msg.substring(15, msg.indexOf("]:"));
            Platform.runLater(() -> {
                TextArea log = privateChatLog.get(target);
                if (log != null) {
                    log.appendText(getTimestamp() + msg + "\n");
                } else {
                    // اگر تبی باز نبود، در جنرال نشان بده
                    activeController.chatArea.appendText(getTimestamp() + msg + "\n");
                }
            });
        }
        else if (msg.startsWith("[Private from ")) {
            String sender = msg.substring(14, msg.indexOf("]:"));
            openPrivateTab(sender, false);
            Platform.runLater(() -> {
                TextArea log = privateChatLog.get(sender);
                if (log != null) log.appendText(getTimestamp() + msg + "\n");
                Tab tab = tabMap.get(sender);
                if (tab != null && !activeController.chatTabPane.getSelectionModel().getSelectedItem().equals(tab)) {
                    tab.setGraphic(createNotificationDot());
                }
            });
        }
        else if (msg.startsWith("[Private to ")) {
            String target = msg.substring(12, msg.indexOf("]:"));
            openPrivateTab(target, false);
            Platform.runLater(() -> {
                TextArea log = privateChatLog.get(target);
                if (log != null) log.appendText(getTimestamp() + msg + "\n");
            });
        }
        else {
            // پیام‌های عمومی و سیستم (مثل Left the chat)
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

    private String getTimestamp() {
        return "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "] ";
    }

    private void listenToServer() {
        try {
            String line;
            while (isRunning) {
                line = in.readLine();
                if (line == null) break;
                final String msg = line;
                Platform.runLater(() -> handleMessageRouting(msg));
            }
        } catch (IOException e) {
            // Network error
        } finally {
            if (isRunning) Platform.runLater(this::showErrorPopup);
        }
    }

    @FXML
    protected void onConnectButtonClick() {
        // Fix 1: Validate name before connecting
        String inputName = nameField.getText().trim();
        if (inputName.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Please enter your name!");
                statusLabel.setStyle("-fx-text-fill: #E57373;");
            }
            return;
        }
        userName = inputName;

        try {
            socket = new Socket(ipField.getText(), Integer.parseInt(portField.getText()));
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(userName);
            isRunning = true;
            new Thread(this::listenToServer).start();
            switchScene("/at/ac/hcw/chat/client/chat-view.fxml");
        } catch (Exception e) {
            if (statusLabel != null) statusLabel.setText("Failed to connect!");
        }
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
            errorStage.setResizable(false);
            errorStage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    protected void onDisconnectButtonClick() {
        isRunning = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
            privateChatLog.clear();
            tabMap.clear();

            // Fix 2: Better stage detection for Error View compatibility
            Stage currentStage = null;
            if (chatTabPane != null) {
                currentStage = (Stage) chatTabPane.getScene().getWindow();
            } else if (ipField != null) {
                currentStage = (Stage) ipField.getScene().getWindow();
            } else {
                // Find the Error window if other fields are null
                List<Window> windows = Window.getWindows().filtered(Window::isFocused);
                if (!windows.isEmpty()) currentStage = (Stage) windows.get(0);
                else currentStage = (Stage) Window.getWindows().get(0);
            }

            if (currentStage != null) {
                switchSceneOnStage(currentStage, "/at/ac/hcw/chat/client/login-view.fxml");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void switchSceneOnStage(Stage stage, String path) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
        Parent root = loader.load();
        stage.setScene(new Scene(root));
        if (path.contains("login")) {
            stage.setTitle("HCW Login");
        } else {
            stage.setTitle("HCW Room");
            ChatController next = loader.getController();
            if (next.welcomeLabel != null) next.welcomeLabel.setText("User: " + userName);
        }
    }

    private void switchScene(String path) {
        try {
            Stage stage = (Stage) (ipField != null ? ipField.getScene().getWindow() : statusLabel.getScene().getWindow());
            switchSceneOnStage(stage, path);
        } catch (Exception e) { e.printStackTrace(); }
    }
}