package org.example;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 12345;
    // Thread-safe set to manage connected clients
    private static Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        // Force IPv4 for compatibility, especially on Mac
        System.setProperty("java.net.preferIPv4Stack" , "true");

        System.out.println("--- Starting Chat Server ---");

        // Displays only the primary IP
        displayPrimaryIP();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is now listening on port: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected from: " + clientSocket.getInetAddress());

                // Start a new thread for each client
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Critical Server Error: " + e.getMessage());
        }
    }

    /**
     * Extracts and prints ONLY the primary local IPv4 address.
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
                        // Look for standard local network ranges
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            System.out.println("Server IP: " + ip);
                            System.out.println("----------------------------");
                            return; // Stop after finding the first valid IP
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Could not extract local IP.");
        }
    }

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
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Add this client to the broadcast list
                clientWriters.add(out);

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Message: " + message);
                    broadcast(message);
                }
            } catch (IOException e) {
                // Client disconnected or connection lost
            } finally {
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
         * Sends the message to all connected clients
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