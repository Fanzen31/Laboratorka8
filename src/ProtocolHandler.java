import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ProtocolHandler {
    private int port;
    private P2PNode node;
    private ServerSocket serverSocket;
    private Socket activeSocket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running;
    private ScheduledExecutorService readerExecutor;

    public ProtocolHandler(int port, P2PNode node) {
        this.port = port;
        this.node = node;
        this.readerExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        new Thread(this::acceptConnections).start();
        node.log("TCP сервер запущен на порту " + port);
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleIncomingConnection(clientSocket);
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleIncomingConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String command = reader.readLine();

            if (command != null) {
                String[] parts = command.split("\\|");
                String cmd = parts[0];

                switch (cmd) {
                    case "CALL_START":
                        String callerNick = parts[1];
                        String callerIp = parts[2];
                        int callerTcpPort = Integer.parseInt(parts[3]);
                        int callerUdpPort = Integer.parseInt(parts[4]);

                        PeerInfo caller = new PeerInfo(callerNick, callerIp, callerTcpPort, callerUdpPort);
                        caller.tcpSocket = socket;

                        node.onIncomingCall(caller);
                        break;

                    case "CALL_ACCEPTED":
                        int peerUdpPort = Integer.parseInt(parts[1]);
                        // Обработка в вызывающем узле
                        break;

                    case "CALL_REJECTED":
                        node.onCallRejected(null);
                        break;

                    case "CALL_END":
                        node.onCallEnded();
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initiateCall(PeerInfo peer) throws IOException {
        activeSocket = new Socket(peer.ip, peer.tcpPort);
        out = new PrintWriter(activeSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(activeSocket.getInputStream()));

        String message = String.format("CALL_START|%s|%s|%d|%d",
                node.getNickname(),
                InetAddress.getLocalHost().getHostAddress(),
                port,
                node.getUdpPort()
        );
        out.println(message);
        node.log("Отправлен CALL_START на " + peer.ip + ":" + peer.tcpPort);

        readerExecutor.submit(() -> waitForResponse(peer));
    }

    private void waitForResponse(PeerInfo peer) {
        try {
            String response = in.readLine();
            if (response != null) {
                if (response.startsWith("CALL_ACCEPTED")) {
                    String[] parts = response.split("\\|");
                    int peerUdpPort = Integer.parseInt(parts[1]);
                    node.onCallAccepted(peer, peerUdpPort);
                } else if (response.equals("CALL_REJECTED")) {
                    node.onCallRejected(peer);
                }
            }
        } catch (IOException e) {
            node.onCallFailed(peer, "Connection error: " + e.getMessage());
        }
    }

    public void acceptCall(PeerInfo peer) throws IOException {
        if (peer.tcpSocket != null && !peer.tcpSocket.isClosed()) {
            out = new PrintWriter(peer.tcpSocket.getOutputStream(), true);
            out.println("CALL_ACCEPTED|" + node.getUdpPort());
            activeSocket = peer.tcpSocket;
            node.log("Отправлен CALL_ACCEPTED");
        }
    }

    public void sendCommand(String command) {
        if (out != null) {
            out.println(command);
            node.log("Отправлена команда: " + command);
        }
    }

    public void sendCommand(Socket socket, String command) {
        try {
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            writer.println(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (activeSocket != null) {
                activeSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        readerExecutor.shutdown();
    }
}