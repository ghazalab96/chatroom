package at.ac.hcw.chat.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Chat Server with Console Logging for all messages.
 */
public class Server {
    private static final int PORT = 12345;
    private static Map<String, PrintWriter> clientMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.out.println("--- HCW Private Chat Server Starting ---");

        displayPrimaryIP();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Server Error: " + e.getMessage());
        }
    }

    private static void displayPrimaryIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                            System.out.println("Server IP: " + ip);
                            System.out.println("----------------------------");
                            return;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("IP Discovery failed.");
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                clientName = in.readLine();

                if (clientName != null && !clientName.isEmpty()) {
                    clientMap.put(clientName, out);
                    System.out.println("[INFO]: " + clientName + " has joined the server.");

                    broadcastUserList();
                    broadcast("[System]: " + clientName + " joined the chat.");
                }

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("@") && message.contains(":")) {
                        // Handle and LOG private message
                        handlePrivateMessage(message);
                    } else {
                        // LOG and Broadcast public message
                        System.out.println("[Public] " + clientName + ": " + message);
                        broadcast(clientName + ": " + message);
                    }
                }
            } catch (IOException e) {
                // Connection lost
            } finally {
                if (clientName != null) {
                    clientMap.remove(clientName);
                    broadcastUserList();
                    broadcast("[System]: " + clientName + " left the chat.");
                    System.out.println("[INFO]: " + clientName + " has disconnected.");
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private void handlePrivateMessage(String rawMessage) {
            try {
                int firstColon = rawMessage.indexOf(":");
                String targetName = rawMessage.substring(1, firstColon).trim();
                String messageContent = rawMessage.substring(firstColon + 1).trim();

                PrintWriter targetWriter = clientMap.get(targetName);
                if (targetWriter != null) {
                    System.out.println("[Private] " + clientName + " to " + targetName + ": " + messageContent);
                    targetWriter.println("[Private from " + clientName + "]: " + messageContent);
                    out.println("[Private to " + targetName + "]: " + messageContent);
                } else {
                  // write the error message for client
                    out.println("[Private Error " + targetName + "]: User not found or offline.");
                }
            } catch (Exception e) {
                out.println("[System]: Invalid private message format.");
            }
        }

        private void broadcast(String message) {
            for (PrintWriter writer : clientMap.values()) {
                writer.println(message);
            }
        }

        private void broadcastUserList() {
            StringBuilder sb = new StringBuilder("USERLIST:");
            for (String name : clientMap.keySet()) {
                sb.append(name).append(",");
            }
            String listMessage = sb.toString();
            for (PrintWriter writer : clientMap.values()) {
                writer.println(listMessage);
            }
        }
    }
}