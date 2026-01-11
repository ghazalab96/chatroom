package at.ac.hcw.chat.server;

import java.io.*; // Importing Input/Output classes for data streaming (Reader/Writer)
import java.net.*; // Importing Networking classes for Sockets and IP discovery
import java.util.*; // Importing Utilities like Map, Set, and Scanner
import java.util.concurrent.ConcurrentHashMap; // A thread-safe Map for high-performance concurrent access

/**
 * Advanced Multi-Threaded Chat Server.
 * Supports: Dynamic Port Input, Private Messaging (@User:), and Online User List synchronization.
 */
public class Server {
    /**
     * ConcurrentHashMap links Usernames (String) to their specific Output streams (PrintWriter).
     * We use "Concurrent" version because multiple 'ClientHandler' threads will
     * add/remove users simultaneously, and standard HashMap is not safe for this.
     */
    private static final Map<String, PrintWriter> clientMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        /*
         * Preference for IPv4 over IPv6.
         * Important for macOS/Linux to ensure the socket uses standard 192.168.x.x addresses.
         */
        System.setProperty("java.net.preferIPv4Stack", "true");

        // Scanner class from java.util allows reading input from the system console (Standard Input)
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- HCW Chat Server Initialization ---");

        /*
         * DYNAMIC PORT INPUT:
         * Instead of a fixed variable, we ask the user to provide the port.
         */
        System.out.print("Please enter the Port number (e.g., 888): ");
        int port;
        try {
            port = Integer.parseInt(scanner.nextLine()); // Parsing the string input into an integer
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number! Using default 888.");
            port = 888;
        }

        // Fetch the primary IP address of this machine to show to the admin
        String primaryIP = getPrimaryIP();

        if (primaryIP == null) {
            System.err.println("CRITICAL ERROR: No active network connection found!");
            return; // Terminate if the machine is not connected to a network
        }

        // Log the final server details to the console
        System.out.println("\n----------------------------");
        System.out.println("SERVER STATUS: ONLINE");
        System.out.println("IP ADDRESS   : " + primaryIP);
        System.out.println("LISTENING ON : Port " + port);
        System.out.println("----------------------------\n");

        /*
         * ServerSocket: The actual networking component that "binds" to the port.
         * It listens for incoming "SYN" packets from clients.
         */
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                /*
                 * .accept(): This method halts execution (blocks) until a client tries to connect.
                 * When a client connects, it returns a 'Socket' object representing the connection.
                 */
                Socket clientSocket = serverSocket.accept();
                System.out.println("[CONNECT]: New client from " + clientSocket.getInetAddress());

                /*
                 * Start a dedicated Thread for this client.
                 * This keeps the 'while(true)' loop free to accept the NEXT client immediately.
                 */
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Socket Error: " + e.getMessage());
        }
    }

    /**
     * Logic to find the primary IPv4 address (e.g., Wi-Fi or Ethernet).
     * Scans all network interfaces and returns the first valid private IP.
     */
    private static String getPrimaryIP() {
        try {
            // Get all hardware network adapters (Loopback, Wi-Fi, Ethernet, etc.)
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Skip disabled, loopback (127.0.0.1), or virtual adapters (VMware/VirtualBox)
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // We only want standard IPv4 addresses
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Filter for common Home/Office IP ranges
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Dedicated Thread class to handle communication for one specific client.
     */
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
                /*
                 * BufferedReader: Wraps the InputStream to read text line-by-line efficiently.
                 * PrintWriter: Wraps the OutputStream to send text. 'true' enables auto-flush.
                 */
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // PROTOCOL: The client must send its username as the first ever message.
                clientName = in.readLine();

                if (clientName != null && !clientName.isEmpty()) {
                    // Save user to the map so we can find them later for private messages
                    clientMap.put(clientName, out);
                    System.out.println("[INFO]: " + clientName + " successfully joined.");

                    broadcastUserList(); // Synchronize the sidebar for all clients
                    broadcast("[System]: " + clientName + " entered the chat.");
                }

                String message;
                // MAIN LISTENING LOOP: Wait for messages from this client
                while ((message = in.readLine()) != null) {
                    /*
                     * Handle Private Message logic if syntax is "@Target: Message"
                     */
                    if (message.startsWith("@") && message.contains(":")) {
                        handlePrivateMessage(message);
                    } else {
                        // Regular Broadcast: Log to console and send to all users
                        System.out.println("[Public] " + clientName + ": " + message);
                        broadcast(clientName + ": " + message);
                    }
                }
            } catch (IOException e) {
                // Occurs if the network is cut or user closes the app
            } finally {
                // CLEANUP: Always run this when the thread ends
                if (clientName != null) {
                    clientMap.remove(clientName);
                    broadcastUserList(); // Update Online Users list for others
                    broadcast("[System]: " + clientName + " has left.");
                    System.out.println("[INFO]: " + clientName + " disconnected.");
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        /**
         * Parses and directs private messages to the correct user.
         */
        private void handlePrivateMessage(String rawMessage) {
            try {
                int colonIndex = rawMessage.indexOf(":");
                String targetName = rawMessage.substring(1, colonIndex).trim(); // Extract name between @ and :
                String content = rawMessage.substring(colonIndex + 1).trim(); // Extract content after :

                PrintWriter targetWriter = clientMap.get(targetName);
                if (targetWriter != null) {
                    System.out.println("[Private] " + clientName + " -> " + targetName + ": " + content);

                    // Send to Receiver
                    targetWriter.println("[Private from " + clientName + "]: " + content);
                    // Send confirmation to Sender's private tab
                    out.println("[Private to " + targetName + "]: " + content);
                } else {
                    // Send error back if target is offline
                    out.println("[Private Error " + targetName + "]: User is not online.");
                }
            } catch (Exception e) {
                out.println("[System]: Error in private message format.");
            }
        }

        /**
         * Sends a message to every connected user.
         */
        private void broadcast(String message) {
            for (PrintWriter writer : clientMap.values()) {
                writer.println(message);
            }
        }

        /**
         * Sends a synchronization string "USERLIST:User1,User2,..." to all clients.
         */
        private void broadcastUserList() {
            StringBuilder sb = new StringBuilder("USERLIST:");
            for (String name : clientMap.keySet()) {
                sb.append(name).append(",");
            }
            String listData = sb.toString();
            for (PrintWriter writer : clientMap.values()) {
                writer.println(listData);
            }
        }
    }
}