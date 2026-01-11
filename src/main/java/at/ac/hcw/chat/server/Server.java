package at.ac.hcw.chat.server;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server Class for HCW Network Chat.
 * This class handles incoming client connections and broadcasts messages.
 */
public class Server {
    // Port number for the server to listen on
    private static final int PORT = 12345;

    // Thread-safe set to manage all connected client output writers
    private static Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        // Essential for Mac/Linux networking to prioritize IPv4
        System.setProperty("java.net.preferIPv4Stack", "true");

        System.out.println("--- HCW Chat Server Starting ---");

        // Display the primary local network IP address
        displayPrimaryIP();

        // Establish the Server Socket
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is now listening on port: " + PORT);

            while (true) {
                // Wait for a new client to connect (Blocking call)
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from: " + clientSocket.getInetAddress());

                // Create and start a dedicated thread for the new client
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Critical Server Error: " + e.getMessage());
        }
    }

    /**
     * Finds and prints ONLY the primary local IPv4 address (e.g., 192.168.x.x).
     */
    private static void displayPrimaryIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Skip loopback, virtual, and inactive interfaces
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();

                        // Filter for common private network ranges (Wi-Fi or LAN)
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            System.out.println("Server IP: " + ip);
                            System.out.println("----------------------------");
                            return;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Could not extract local IP.");
        }
    }

    /**
     * Inner class to handle individual client threads.
     * This is where the server "listens" to the clients.
     */
    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Initialize input and output streams
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Register this client's writer to the global broadcast set
                clientWriters.add(out);

                String message;
                // Continuously read messages from the client until they disconnect
                while ((message = in.readLine()) != null) {
                    System.out.println("Broadcasting: " + message);
                    broadcast(message);
                }
            } catch (IOException e) {
                // Expected when a client loses connection
            } finally {
                // Cleanup: Remove client from set and close the socket
                if (out != null) {
                    clientWriters.remove(out);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("A client has left the chat.");
            }
        }

        /**
         * Sends a message to all currently connected clients.
         */
        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message);
                }
            }
        }
    }
}