package org.example;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Professional Multi-Client Chat Server
 * Features: IP Discovery, Multi-threading, and Global Broadcasting.
 */
public class Server {
    // The port on which the server will listen
    private static final int PORT = 12345;

    // Thread-safe set to store all active client output writers
    private static final Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        // Force the JVM to prefer IPv4 over IPv6 for easier networking
        System.setProperty("java.net.preferIPv4Stack", "true");

        System.out.println("--- Chat Server Initialization ---");

        // 1. Discover and display the primary local IP address
        String primaryIP = getPrimaryIP();
        if (primaryIP == null) {
            System.err.println("CRITICAL ERROR: No active network connection found!");
            System.err.println("Please connect to Wi-Fi or LAN and restart the server.");
            return; // Terminate if no network is available
        }

        System.out.println("Server IP Address: " + primaryIP);
        System.out.println("Server Port      : " + PORT);
        System.out.println("----------------------------------");

        // 2. Start the Server Socket
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is ONLINE. Waiting for connections...");

            while (true) {
                // Wait for a client to connect
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection established with: " + clientSocket.getInetAddress());

                // Create and start a dedicated thread for the new client
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Server error occurred: " + e.getMessage());
        }
    }

    /**
     * Finds the primary local IPv4 address (Wi-Fi/Ethernet).
     * Skips virtual, loopback, and inactive interfaces.
     */
    private static String getPrimaryIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();

                // Only consider active, non-loopback, non-virtual interfaces
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;

                Enumeration<InetAddress> addresses = ifaceToAddresses(nif);
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Return the first standard private network IP found
                        if (isPrivateIP(ip)) {
                            return ip;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            return null;
        }
        return null;
    }

    private static Enumeration<InetAddress> ifaceToAddresses(NetworkInterface nif) {
        return nif.getInetAddresses();
    }

    private static boolean isPrivateIP(String ip) {
        return ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.");
    }

    /**
     * Inner class to handle individual client communication
     */
    private static class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Setup Input and Output streams
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Register client to the broadcast list
                clientWriters.add(out);

                String message;
                // Keep listening for messages until client disconnects
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) break;

                    System.out.println("Relaying message: " + message);
                    broadcast(message);
                }
            } catch (IOException e) {
                // Client connection lost (unexpectedly)
            } finally {
                cleanup();
            }
        }

        /**
         * Cleans up resources and removes the client from the active list
         */
        private void cleanup() {
            if (out != null) {
                clientWriters.remove(out);
            }
            try {
                socket.close();
                System.out.println("A client has disconnected safely.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Sends a message to all currently connected clients
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

