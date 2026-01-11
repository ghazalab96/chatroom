package at.ac.hcw.chat.client;

import javafx.application.Platform; // Used to run UI updates from background threads
import javafx.fxml.FXML; // Annotation to link UI elements from FXML to Java code
import javafx.fxml.FXMLLoader; // Class to load FXML layout files
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*; // Importing UI controls (TextField, Button, TabPane, etc.)
import javafx.scene.shape.Circle; // Used for the notification red dot
import javafx.stage.Stage; // Represents a window in JavaFX
import javafx.stage.Window; // Parent class of Stage, used for window searching
import java.io.*; // Classes for reading and writing data streams
import java.net.Socket; // Class for establishing TCP network connections
import java.time.LocalTime; // For generating message timestamps
import java.time.format.DateTimeFormatter; // For formatting the time as [HH:mm]
import java.util.*; // Utility classes like Map, List, and HashMap

/**
 * Main Controller class for the Chat Application.
 * It manages the Login sequence, Chat Room interaction, Private Messaging,
 * and Error handling when the server goes offline.
 */
public class ChatController {

    // --- FXML UI Injections (Mapped to login-view.fxml, chat-view.fxml, and error-view.fxml) ---
    @FXML private TextField ipField, portField, nameField;
    @FXML private Label statusLabel; // Displays connection errors on the Login screen
    @FXML private TextArea chatArea; // The main text area for the "General" public chat
    @FXML private TextField messageField; // Input field for typing messages
    @FXML private Label welcomeLabel; // Displays "Chatting as: [Name]"
    @FXML private ListView<String> userListView; // Sidebar showing all online users
    @FXML private TabPane chatTabPane; // Container for General and Private chat tabs

    // --- Networking Static Variables (Static to persist across different scene instances) ---
    private static Socket socket; // The physical TCP connection to the server
    private static PrintWriter out; // Outgoing stream (Client -> Server)
    private static BufferedReader in; // Incoming stream (Server -> Client)
    private static String userName; // Stores the local user's chosen name
    private static volatile boolean isRunning = false; // Flag to control the listening loop thread

    /**
     * activeController: A static reference to the controller instance currently visible on screen.
     * Since JavaFX creates a NEW controller instance every time a scene switches,
     * this allows background threads to always find and update the correct UI components.
     */
    private static ChatController activeController;

    /**
     * Data structures to manage private conversations.
     * privateChatLog: Maps a username to its specific TextArea log.
     * tabMap: Maps a username to its specific Tab object for UI management.
     */
    private static final Map<String, TextArea> privateChatLog = new HashMap<>();
    private static final Map<String, Tab> tabMap = new HashMap<>();

    /**
     * Automatically called by JavaFX after the FXML is loaded.
     */
    @FXML
    public void initialize() {
        activeController = this; // Register the current instance as the active UI controller

        // Logic for handling double-clicks on the user sidebar
        if (userListView != null) {
            userListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) { // Detect rapid double-click
                    String selectedUser = userListView.getSelectionModel().getSelectedItem();
                    // Open a private tab if the user is not clicking themselves
                    if (selectedUser != null && !selectedUser.equals(userName)) {
                        openPrivateTab(selectedUser, true); // true = switch focus to the new tab
                    }
                }
            });
        }

        // Listener to remove the red notification dot when the user clicks on a tab
        if (chatTabPane != null) {
            chatTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null) newTab.setGraphic(null);
            });
        }
    }

    /**
     * Utility to create a small red circle used for unread message notifications.
     */
    private Circle createNotificationDot() {
        Circle dot = new Circle(5);
        dot.setStyle("-fx-fill: #FF5252; -fx-stroke: white; -fx-stroke-width: 1;");
        return dot;
    }

    /**
     * Logic to create or switch to a private chat tab.
     * @param targetUser The user to chat with.
     * @param shouldFocus If true, the app switches focus to this tab immediately.
     */
    private void openPrivateTab(String targetUser, boolean shouldFocus) {
        // Platform.runLater ensures UI changes happen on the main JavaFX thread
        Platform.runLater(() -> {
            if (activeController == null || activeController.chatTabPane == null) return;

            Tab targetTab = tabMap.get(targetUser);

            // If the tab doesn't exist yet, create it
            if (targetTab == null) {
                TextArea newLog = new TextArea();
                newLog.setEditable(false);
                newLog.setWrapText(true);
                newLog.setStyle("-fx-background-radius: 0 0 15 15;"); // Rounded bottom corners

                targetTab = new Tab(targetUser, newLog);
                targetTab.setClosable(true);

                // Cleanup maps when the user closes the tab
                targetTab.setOnClosed(e -> {
                    privateChatLog.remove(targetUser);
                    tabMap.remove(targetUser);
                });

                activeController.chatTabPane.getTabs().add(targetTab);
                privateChatLog.put(targetUser, newLog);
                tabMap.put(targetUser, targetTab);
            }

            // If requested (via double-click), bring the tab to the front
            if (shouldFocus) {
                activeController.chatTabPane.getSelectionModel().select(targetTab);
                targetTab.setGraphic(null); // Clear notification if we are looking at it
            }
        });
    }

    /**
     * The Protocol Parser. Examines strings from the server and directs them to the correct UI.
     */
    private void handleMessageRouting(String msg) {
        if (activeController == null) return;

        // 1. Online User List Update protocol
        if (msg.startsWith("USERLIST:")) {
            String[] users = msg.substring(9).split(",");
            activeController.userListView.getItems().clear();
            for (String u : users) if (!u.isEmpty()) activeController.userListView.getItems().add(u);
        }
        // 2. Private Message Error protocol (Target user found offline)
        else if (msg.startsWith("[Private Error ")) {
            String target = msg.substring(15, msg.indexOf("]:"));
            Platform.runLater(() -> {
                TextArea log = privateChatLog.get(target);
                if (log != null) log.appendText(getTimestamp() + msg + "\n");
                else activeController.chatArea.appendText(getTimestamp() + msg + "\n");
            });
        }
        // 3. Incoming Private Message protocol
        else if (msg.startsWith("[Private from ")) {
            String sender = msg.substring(14, msg.indexOf("]:"));
            openPrivateTab(sender, false); // Create tab in background
            Platform.runLater(() -> {
                TextArea log = privateChatLog.get(sender);
                if (log != null) log.appendText(getTimestamp() + msg + "\n");
                // Show red dot if the user is not currently viewing this sender's tab
                Tab tab = tabMap.get(sender);
                if (tab != null && !activeController.chatTabPane.getSelectionModel().getSelectedItem().equals(tab)) {
                    tab.setGraphic(createNotificationDot());
                }
            });
        }
        // 4. Outgoing Private Message Confirmation protocol
        else if (msg.startsWith("[Private to ")) {
            String target = msg.substring(12, msg.indexOf("]:"));
            openPrivateTab(target, false);
            Platform.runLater(() -> {
                TextArea log = privateChatLog.get(target);
                if (log != null) log.appendText(getTimestamp() + msg + "\n");
            });
        }
        // 5. Standard Public/System Messages
        else {
            if (activeController.chatArea != null) {
                activeController.chatArea.appendText(getTimestamp() + msg + "\n");
                // Show notification on General tab if user is currently in a private chat
                Tab generalTab = activeController.chatTabPane.getTabs().get(0);
                if (!activeController.chatTabPane.getSelectionModel().getSelectedItem().equals(generalTab)) {
                    generalTab.setGraphic(createNotificationDot());
                }
            }
        }
    }

    /**
     * Logic for the "Send" button. Automatically detects if the message is public or private.
     */
    @FXML
    protected void onSendButtonClick() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty() || out == null) return;

        // Find which tab is active to decide the destination
        Tab selectedTab = chatTabPane.getSelectionModel().getSelectedItem();
        String tabName = selectedTab.getText();

        if (tabName.equals("General")) {
            out.println(msg); // Send to everyone
        } else {
            out.println("@" + tabName + ": " + msg); // Prefix with @ for private server logic
        }
        messageField.clear(); // Clear input after sending
    }

    /**
     * Returns the current time formatted as [HH:mm].
     */
    private String getTimestamp() {
        return "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "] ";
    }

    /**
     * Background thread that blocks on readLine() until the server sends a message.
     */
    private void listenToServer() {
        try {
            String line;
            while (isRunning) {
                line = in.readLine();
                if (line == null) break; // Server disconnected
                final String msg = line;
                Platform.runLater(() -> handleMessageRouting(msg));
            }
        } catch (IOException e) {
            // Expected when closing the socket
        } finally {
            // If the loop breaks but user didn't disconnect, the server must have crashed
            if (isRunning) Platform.runLater(this::showErrorPopup);
        }
    }

    /**
     * Initial connection logic called from the login view.
     */
    @FXML
    protected void onConnectButtonClick() {
        // Validation for Name input
        String inputName = nameField.getText().trim();
        if (inputName.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Please enter your name!");
                statusLabel.setStyle("-fx-text-fill: #E57373;"); // Muted red error color
            }
            return;
        }
        userName = inputName;

        try {
            // Establish connection using the IP and Port from the text fields
            socket = new Socket(ipField.getText().trim(), Integer.parseInt(portField.getText().trim()));
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(userName); // First message of the protocol is the name
            isRunning = true;

            // Start the receiver thread
            new Thread(this::listenToServer).start();

            // Switch current window to the chat room layout
            switchScene("/at/ac/hcw/chat/client/chat-view.fxml");
        } catch (Exception e) {
            if (statusLabel != null) statusLabel.setText("Failed to connect!");
        }
    }

    /**
     * Closes the main chat window and opens the standalone Error Window.
     */
    private void showErrorPopup() {
        try {
            isRunning = false;
            if (socket != null) socket.close();

            // Close the current chat window instance
            if (activeController != null && activeController.chatTabPane != null) {
                ((Stage) activeController.chatTabPane.getScene().getWindow()).close();
            }

            // Load and display the error scene
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/ac/hcw/chat/client/error-view.fxml"));
            Stage errorStage = new Stage();
            errorStage.setScene(new Scene(loader.load()));
            errorStage.setTitle("Connection Error");
            errorStage.setResizable(false);
            errorStage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Handles clean disconnection and ensures maps are cleared for the next session.
     */
    @FXML
    protected void onDisconnectButtonClick() {
        isRunning = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();

            // Clear maps to prevent UI leaks
            privateChatLog.clear();
            tabMap.clear();

            // Find the current window based on which UI elements are available
            Stage currentStage = null;
            if (chatTabPane != null) {
                currentStage = (Stage) chatTabPane.getScene().getWindow();
            } else if (ipField != null) {
                currentStage = (Stage) ipField.getScene().getWindow();
            } else {
                // Fallback for the Error View: find the currently focused stage
                List<Window> windows = Window.getWindows().filtered(Window::isFocused);
                if (!windows.isEmpty()) currentStage = (Stage) windows.get(0);
                else currentStage = (Stage) Window.getWindows().get(0);
            }

            if (currentStage != null) {
                switchSceneOnStage(currentStage, "/at/ac/hcw/chat/client/login-view.fxml");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Helper to load FXML and set the Scene on a specific window (Stage).
     */
    private void switchSceneOnStage(Stage stage, String path) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
        Parent root = loader.load();
        stage.setScene(new Scene(root));

        if (path.contains("login")) {
            stage.setTitle("HCW Chat - Login");
        } else {
            stage.setTitle("HCW Chat - Room");
            ChatController next = loader.getController();
            if (next.welcomeLabel != null) next.welcomeLabel.setText("Chatting as: " + userName);
        }
    }

    /**
     * Standard scene switcher for the current active window.
     */
    private void switchScene(String path) {
        try {
            Stage stage = (Stage) (ipField != null ? ipField.getScene().getWindow() : statusLabel.getScene().getWindow());
            switchSceneOnStage(stage, path);
        } catch (Exception e) { e.printStackTrace(); }
    }
}