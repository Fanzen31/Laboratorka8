import java.util.concurrent.*;

public class CallManager {
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_DELAY_MS = 10000;

    private P2PNode node;
    private ScheduledExecutorService scheduler;
    private PeerInfo lastCalledPeer;
    private ScheduledFuture<?> pendingRedial;
    private boolean inCall = false;

    public CallManager(P2PNode node) {
        this.node = node;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void initiateCall(PeerInfo peer) {
        cancelRedial();
        lastCalledPeer = peer;

        if (peer.retryCount == 0) {
            peer.resetRetry();
        }

        inCall = false;
        node.log("Вызов " + peer.nickname + " (попытка " + (peer.retryCount + 1) + "/" + MAX_RETRY_COUNT + ")");

        try {
            node.getProtocolHandler().initiateCall(peer);
        } catch (Exception e) {
            node.onCallFailed(peer, e.getMessage());
        }
    }

    public void acceptCall(PeerInfo peer) {
        cancelRedial();
        peer.resetRetry();
        inCall = true;

        try {
            node.getProtocolHandler().acceptCall(peer);
        } catch (Exception e) {
            node.onCallFailed(peer, e.getMessage());
        }
    }

    public void endCall() {
        inCall = false;
        cancelRedial();

        if (lastCalledPeer != null) {
            lastCalledPeer.resetRetry();
        }

        if (node.getProtocolHandler() != null) {
            node.getProtocolHandler().sendCommand("CALL_END");
        }
        node.onCallEnded();
    }

    public boolean shouldRetry(PeerInfo peer) {
        if (inCall) return false;
        if (peer == null) return false;
        if (peer.retryCount >= MAX_RETRY_COUNT) return false;
        return true;
    }

    public void scheduleRedial(PeerInfo peer) {
        if (inCall) {
            node.log("Уже в разговоре, перезвон отменён");
            return;
        }

        cancelRedial();

        if (!shouldRetry(peer)) {
            node.log("Превышено количество попыток для " + peer.nickname);
            node.onCallFailed(peer, "Превышено количество попыток");
            return;
        }

        peer.incrementRetry();
        lastCalledPeer = peer;

        node.log("Повторная попытка " + peer.retryCount + "/" + MAX_RETRY_COUNT +
                " для " + peer.nickname + " через 10 сек");

        pendingRedial = scheduler.schedule(() -> {
            if (!inCall && shouldRetry(peer)) {
                node.log("Автоматический перезвон " + peer.nickname);
                initiateCall(peer);
            }
            pendingRedial = null;
        }, RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public void cancelRedial() {
        if (pendingRedial != null && !pendingRedial.isDone()) {
            pendingRedial.cancel(false);
            pendingRedial = null;
        }
    }

    public PeerInfo getLastCalledPeer() {
        return lastCalledPeer;
    }

    public void reset() {
        cancelRedial();
        inCall = false;
        if (lastCalledPeer != null) {
            lastCalledPeer.resetRetry();
        }
    }
}