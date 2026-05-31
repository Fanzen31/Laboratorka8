import java.net.Socket;

public class PeerInfo {
    public String nickname;
    public String ip;
    public int tcpPort;
    public int udpPort;
    public long lastSeen;
    public Socket tcpSocket;
    public int retryCount;
    public long lastCallAttempt;

    public PeerInfo(String nickname, String ip, int tcpPort) {
        this.nickname = nickname;
        this.ip = ip;
        this.tcpPort = tcpPort;
        this.udpPort = tcpPort + 1;
        this.lastSeen = System.currentTimeMillis();
        this.retryCount = 0;
    }

    public PeerInfo(String nickname, String ip, int tcpPort, int udpPort) {
        this(nickname, ip, tcpPort);
        this.udpPort = udpPort;
    }

    public void updateSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public void incrementRetry() {
        retryCount++;
    }

    public void resetRetry() {
        retryCount = 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PeerInfo) {
            PeerInfo other = (PeerInfo) obj;
            return nickname.equals(other.nickname) && ip.equals(other.ip);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return nickname.hashCode() + ip.hashCode();
    }
}