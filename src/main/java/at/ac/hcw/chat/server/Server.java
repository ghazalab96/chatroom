package at.ac.hcw.chat.server;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server Class for HCW Network Chat
 * Location: at.ac.hcw.chat.server
 */
public class Server {
    private static final int PORT = 12345;

    // Thread-safe set to manage all connected clients
    private static Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        // Essential for Mac networking to prefer IPv4
        System.setProperty("java.net.preferIPv4Stack", "true");

        System.out.println("--- Starting HCW Chat Server ---");

        // Step 1: Discover and display ONLY the primary network IP
        displayPrimaryIP();

        // Step 2: Start the ServerSocket
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is now listening on port: " + PORT);

            while (true) {
                // Wait for a new client to connect
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from: " + clientSocket.getInetAddress());

                // Start a new thread for each client
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

                // Skip loopback, virtual and inactive interfaces
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();

                        // Filter for common local network ranges (Wi-Fi or LAN)
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            System.out.println("Server IP: " + ip);
                            System.out.println("----------------------------");
                            return; // Found the primary IP, stop searching
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Could not extract local IP.");
        }
    }

    /**
     * Inner class to handle individual client threads
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

                // Add this client to the broadcast list
                clientWriters.add(out);

                String message;
                // Keep reading messages until client disconnects
                while ((message = in.readLine()) != null) {
                    System.out.println("Broadcast: " + message);
                    broadcast(message);
                }
            } catch (IOException e) {
                // Connection lost
            } finally {
                // Remove client and clean up resources
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
         * Sends a message to all connected clients
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