import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class LanDiscovery {
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 8888;
    private static final int ANNOUNCE_INTERVAL = 5000;

    private String nickname;
    private String ip;
    private int tcpPort;
    private P2PNode node;

    private MulticastSocket multicastSocket;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;

    public LanDiscovery(String nickname, String ip, int tcpPort, P2PNode node) {
        this.nickname = nickname;
        this.ip = ip;
        this.tcpPort = tcpPort;
        this.node = node;
    }

    public void start() {
        running = true;
        scheduler = Executors.newScheduledThreadPool(2);

        scheduler.submit(this::listenForAnnouncements);
        scheduler.scheduleAtFixedRate(this::sendAnnouncement, 0, ANNOUNCE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void sendAnnouncement() {
        try {
            if (multicastSocket == null || multicastSocket.isClosed()) {
                multicastSocket = new MulticastSocket();
                multicastSocket.setTimeToLive(1);
            }

            String message = String.format("HELLO\t%s\t%s\t%d", nickname, ip, tcpPort);
            byte[] data = message.getBytes("UTF-8");

            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName(MULTICAST_ADDRESS),
                    MULTICAST_PORT
            );

            multicastSocket.send(packet);

        } catch (Exception e) {
            System.err.println("Error sending announcement: " + e.getMessage());
        }
    }

    private void listenForAnnouncements() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(new InetSocketAddress(group, MULTICAST_PORT), NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));

            byte[] buffer = new byte[1024];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    String senderIp = packet.getAddress().getHostAddress();

                    processAnnouncement(message, senderIp);

                } catch (IOException e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }

            socket.leaveGroup(group);

        } catch (Exception e) {
            System.err.println("Error in multicast listener: " + e.getMessage());
        }
    }

    private void processAnnouncement(String message, String senderIp) {
        String[] parts = message.split("\t");

        if (parts.length >= 4 && parts[0].equals("HELLO")) {
            String remoteNick = parts[1];
            String remoteIp = parts[2];
            int remoteTcpPort = Integer.parseInt(parts[3]);

            if (remoteNick.equals(nickname) && remoteIp.equals(ip)) {
                return;
            }

            PeerInfo peer = new PeerInfo(remoteNick, remoteIp, remoteTcpPort);
            peer.updateSeen();

            node.onPeerDiscovered(peer);
        }
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (multicastSocket != null && !multicastSocket.isClosed()) {
            multicastSocket.close();
        }
    }
}