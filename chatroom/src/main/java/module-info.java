module org.example.chatroom {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.chatroom to javafx.fxml;
    exports org.example.chatroom;
}