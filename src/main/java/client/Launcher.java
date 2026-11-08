package client;

public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        javafx.application.Application.launch(ClienteChat.class, args);
    }
}
