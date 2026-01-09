package org.example.chatroom;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.io.*;
import java.net.Socket;

public class HelloController {
    @FXML private TextField ipField;
    @FXML private TextField portField; // حتما این باشد
    @FXML private TextField nameField;
    @FXML private TextField messageField;
    @FXML private TextArea chatArea;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Button sendButton;

    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;
    private volatile boolean isRunning = false; // برای کنترل حلقه دریافت پیام

    @FXML
    protected void onConnectButtonClick() {
        startConnection();
    }

    private void startConnection() {
        String ip = ipField.getText();
        int port = 12345;

        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            isRunning = true;
            updateUI(true);
            chatArea.appendText("[System]: Connected to server.\n");

            // ترد برای دریافت پیام‌ها
            new Thread(this::listenToServer).start();

        } catch (IOException e) {
            chatArea.appendText("[System]: Server not found. Retrying in 3 seconds...\n");
            // اگر بار اول وصل نشد، می‌توانی مکانیزم ری‌کانکت را اینجا هم صدا کنی
            attemptReconnect();
        }
    }

    private void listenToServer() {
        try {
            String line;
            while (isRunning && (line = in.readLine()) != null) {
                String finalLine = line;
                Platform.runLater(() -> chatArea.appendText(finalLine + "\n"));
            }
        } catch (IOException e) {
            if (isRunning) { // اگر خودمان دیسکانکت نکرده باشیم و ارور بدهد، یعنی سرور قطع شده
                Platform.runLater(() -> chatArea.appendText("[Alert]: Server went down!\n"));
                attemptReconnect();
            }
        }
    }

    // متد برای تلاش مجدد خودکار
    private void attemptReconnect() {
        updateUI(false);
        new Thread(() -> {
            while (isRunning && (socket == null || socket.isClosed())) {
                try {
                    Thread.sleep(3000); // ۳ ثانیه صبر کن و دوباره تست کن
                    String ip = ipField.getText();
                    socket = new Socket(ip, 12345);

                    // اگر به اینجا برسد یعنی وصل شده
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    Platform.runLater(() -> {
                        chatArea.appendText("[Success]: Server back online!\n");
                        updateUI(true);
                    });
                    new Thread(this::listenToServer).start();
                    break;
                } catch (Exception e) {
                    // هنوز وصل نشده... حلقه ادامه می‌یابد
                }
            }
        }).start();
    }

    @FXML
    protected void onDisconnectButtonClick() {
        isRunning = false;
        closeConnection();
        chatArea.appendText("[System]: You disconnected.\n");
        updateUI(false);
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onSendButtonClick() {
        String msg = messageField.getText();
        if (!msg.isEmpty() && out != null) {
            out.println(nameField.getText() + ": " + msg);
            messageField.clear();
        }
    }

    // متد کمکی برای فعال/غیرفعال کردن دکمه‌ها
    private void updateUI(boolean connected) {
        Platform.runLater(() -> {
            connectButton.setDisable(connected);
            disconnectButton.setDisable(!connected);
            sendButton.setDisable(!connected);
            messageField.setDisable(!connected);
            ipField.setDisable(connected);
            nameField.setDisable(connected);
        });
    }
}