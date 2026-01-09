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
        displayLocalIPs();

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
     * Extracts and prints all local IPv4 addresses.
     * Use these IPs in the Client app to connect.
     */
    private static void displayLocalIPs() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        System.out.println("Available IP: " + addr.getHostAddress());
                    }
                }
            }
            System.out.println("----------------------------");
        } catch (SocketException e) {
            System.out.println("Could not extract local IPs.");
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