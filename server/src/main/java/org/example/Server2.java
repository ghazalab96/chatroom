package org.example;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server2: Multi-client Chat Server with IP Discovery and Broadcast
 */
public class Server2 {

    // A thread-safe set to keep track of all connected client output writers
    private static Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        try {
            // 1. Try to extract local IPv4 address
            String ip = getLocalIPv4();

            // 2. If no network is found, stop the server
            if (ip == null) {
                System.err.println("################################################");
                System.err.println("ERROR: No active network connection found!");
                System.err.println("Please connect to Wi-Fi or LAN and try again.");
                System.err.println("################################################");
                return;
            }

            // 3. Ask for the port number
            Scanner scanner = new Scanner(System.in);
            System.out.println("Your Machine IP: " + ip);
            System.out.print("Enter Port (e.g., 12345): ");
            int port = scanner.nextInt();

            // 4. Start the ServerSocket
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("----------------------------------------------");
                System.out.println("SERVER ONLINE: " + ip + " on port " + port);
                System.out.println("Waiting for clients...");
                System.out.println("----------------------------------------------");

                while (true) {
                    // Wait for a client to connect
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected from: " + clientSocket.getInetAddress());

                    // Start a new thread for each client to handle communication
                    new ClientHandler(clientSocket).start();
                }
            }
        } catch (Exception e) {
            System.err.println("Server encountered an error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Inner class to handle each client in a separate thread
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
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Add this client to the set of broadcast targets
                clientWriters.add(out);

                String received;
                while ((received = in.readLine()) != null) {
                    // If client sends 'exit' or disconnects
                    if (received.equalsIgnoreCase("exit")) {
                        break;
                    }
                    System.out.println("Broadcast message: " + received);
                    broadcast(received);
                }
            } catch (IOException e) {
                // Connection lost with this specific client
            } finally {
                // Cleanup when client leaves
                if (out != null) {
                    clientWriters.remove(out);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("A client disconnected.");
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

    /**
     * Finds the local IPv4 address of the computer (Wi-Fi or Ethernet)
     */
    public static String getLocalIPv4() throws SocketException {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        while (nets.hasMoreElements()) {
            NetworkInterface nif = nets.nextElement();

            // Skip loopback, virtual and inactive interfaces
            if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;

            for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                InetAddress addr = ia.getAddress();

                // Check for IPv4 only
                if (addr instanceof Inet4Address) {
                    String ip = addr.getHostAddress();
                    if (isPrivateIPv4(ip)) {
                        return ip;
                    }
                }
            }
        }
        return null; // Return null if no network found
    }

    /**
     * Checks if the IP address belongs to common private network ranges
     */
    private static boolean isPrivateIPv4(String ip) {
        return ip.startsWith("192.168.") || // Class C
                ip.startsWith("10.") ||       // Class A
                ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"); // Class B (172.16.x.x - 172.31.x.x)
    }
}