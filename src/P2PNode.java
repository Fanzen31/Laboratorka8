import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class P2PNode {
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 8888;
    private static final int HEARTBEAT_INTERVAL = 3000;
    private static final int HEARTBEAT_TIMEOUT = 15000;

    private String nickname;
    private int tcpPort;
    private int udpPort;
    private InetAddress localAddress;

    private AudioManager audioManager;
    private CallManager callManager;
    private LanDiscovery lanDiscovery;
    private ProtocolHandler protocolHandler;

    private Map<String, PeerInfo> discoveredPeers;
    private DefaultListModel<String> peerListModel;
    private JList<String> peerList;

    private JFrame frame;
    private JTextArea statusArea;
    private JButton startListenBtn;
    private JButton callBtn;
    private JButton endCallBtn;
    private JButton redialBtn;
    private JButton muteBtn;

    private JTextField nicknameField;
    private JTextField tcpPortField;
    private JTextField contactNameField;
    private JTextField contactIpField;
    private JTextField contactPortField;

    private JLabel myInfoLabel;
    private JLabel statusLabel;
    private JLabel callTimerLabel;
    private javax.swing.Timer callTimer;
    private int callDurationSeconds;

    private boolean isListening = false;
    private PeerInfo currentCallPeer;
    private boolean isMuted = false;

    public P2PNode() {
        discoveredPeers = new HashMap<>();
        peerListModel = new DefaultListModel<>();
        audioManager = new AudioManager();
        callManager = new CallManager(this);

        try {
            localAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            localAddress = InetAddress.getLoopbackAddress();
        }

        initGUI();
    }

    private void initGUI() {
        frame = new JFrame("P2P VoIP - Голосовой звонок");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 750);
        frame.setLayout(new BorderLayout());

        // ========== ВЕРХНЯЯ ПАНЕЛЬ - МОИ ДАННЫЕ ==========
        JPanel myDataPanel = new JPanel(new GridBagLayout());
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.RED, 2),
                "⚙️ МОИ ДАННЫЕ (заполните и нажмите ПРИМЕНИТЬ)"
        );
        titledBorder.setTitleJustification(TitledBorder.LEFT);
        myDataPanel.setBorder(titledBorder);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Поле для ника
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel nickLabel = new JLabel("Мой ник:");
        nickLabel.setFont(new Font("Arial", Font.BOLD, 12));
        myDataPanel.add(nickLabel, gbc);

        gbc.gridx = 1;
        nicknameField = new JTextField(15);
        nicknameField.setFont(new Font("Arial", Font.PLAIN, 14));
        nicknameField.setText("Пользователь");
        myDataPanel.add(nicknameField, gbc);

        // Поле для порта
        gbc.gridx = 2;
        JLabel portLabel = new JLabel("Мой TCP порт:");
        portLabel.setFont(new Font("Arial", Font.BOLD, 12));
        myDataPanel.add(portLabel, gbc);

        gbc.gridx = 3;
        tcpPortField = new JTextField(8);
        tcpPortField.setFont(new Font("Arial", Font.PLAIN, 14));
        tcpPortField.setText("5000");
        myDataPanel.add(tcpPortField, gbc);

        // Кнопка ПРИМЕНИТЬ
        gbc.gridx = 4;
        JButton applyBtn = new JButton("✅ ПРИМЕНИТЬ");
        applyBtn.setFont(new Font("Arial", Font.BOLD, 12));
        applyBtn.setBackground(new Color(76, 175, 80));
        applyBtn.setForeground(Color.WHITE);
        applyBtn.setPreferredSize(new Dimension(120, 35));
        applyBtn.addActionListener(e -> applySettings());
        myDataPanel.add(applyBtn, gbc);

        // Кнопка ЗАПУСТИТЬ ОЖИДАНИЕ
        gbc.gridx = 5;
        startListenBtn = new JButton("🟢 ЗАПУСТИТЬ ОЖИДАНИЕ");
        startListenBtn.setFont(new Font("Arial", Font.BOLD, 12));
        startListenBtn.setBackground(new Color(33, 150, 243));
        startListenBtn.setForeground(Color.WHITE);
        startListenBtn.setPreferredSize(new Dimension(160, 35));
        startListenBtn.addActionListener(e -> startListening());
        myDataPanel.add(startListenBtn, gbc);

        // Информация о моих данных
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 6;
        myInfoLabel = new JLabel("📋 Мои данные не заданы. Заполните поля выше и нажмите ПРИМЕНИТЬ");
        myInfoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        myInfoLabel.setForeground(Color.RED);
        myInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        myDataPanel.add(myInfoLabel, gbc);

        frame.add(myDataPanel, BorderLayout.NORTH);

        // ========== ПАНЕЛЬ РУЧНОГО ДОБАВЛЕНИЯ КОНТАКТА ==========
        JPanel addContactPanel = new JPanel(new GridBagLayout());
        addContactPanel.setBorder(BorderFactory.createTitledBorder("➕ ДОБАВИТЬ КОНТАКТ ВРУЧНУЮ"));

        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(5, 5, 5, 5);

        gbc2.gridx = 0;
        gbc2.gridy = 0;
        addContactPanel.add(new JLabel("Имя:"), gbc2);

        gbc2.gridx = 1;
        contactNameField = new JTextField("Друг", 10);
        addContactPanel.add(contactNameField, gbc2);

        gbc2.gridx = 2;
        addContactPanel.add(new JLabel("IP:"), gbc2);

        gbc2.gridx = 3;
        contactIpField = new JTextField("192.168.1.100", 12);
        addContactPanel.add(contactIpField, gbc2);

        gbc2.gridx = 4;
        addContactPanel.add(new JLabel("Порт:"), gbc2);

        gbc2.gridx = 5;
        contactPortField = new JTextField("5000", 6);
        addContactPanel.add(contactPortField, gbc2);

        gbc2.gridx = 6;
        JButton addBtn = new JButton("➕ ДОБАВИТЬ КОНТАКТ");
        addBtn.setBackground(new Color(255, 152, 0));
        addBtn.setForeground(Color.WHITE);
        addBtn.setFont(new Font("Arial", Font.BOLD, 11));
        addBtn.addActionListener(e -> addManualContact());
        addContactPanel.add(addBtn, gbc2);

        frame.add(addContactPanel, BorderLayout.NORTH);

        // ========== ЦЕНТРАЛЬНАЯ ПАНЕЛЬ ==========
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setResizeWeight(0.4);

        // ЛЕВАЯ ПАНЕЛЬ - СПИСОК КОНТАКТОВ
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("📞 МОИ КОНТАКТЫ"));

        peerList = new JList<>(peerListModel);
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerList.setFont(new Font("Arial", Font.PLAIN, 14));
        leftPanel.add(new JScrollPane(peerList), BorderLayout.CENTER);

        JPanel leftButtonPanel = new JPanel(new FlowLayout());

        JButton autoDiscoveryBtn = new JButton("🔍 АВТОПОИСК");
        autoDiscoveryBtn.addActionListener(e -> startDiscovery());
        leftButtonPanel.add(autoDiscoveryBtn);

        JButton refreshBtn = new JButton("🔄 ОБНОВИТЬ");
        refreshBtn.addActionListener(e -> refreshPeerList());
        leftButtonPanel.add(refreshBtn);

        JButton deleteBtn = new JButton("❌ УДАЛИТЬ");
        deleteBtn.addActionListener(e -> deleteSelectedContact());
        leftButtonPanel.add(deleteBtn);

        leftPanel.add(leftButtonPanel, BorderLayout.SOUTH);

        centerSplit.setLeftComponent(leftPanel);

        // ПРАВАЯ ПАНЕЛЬ - ИНФОРМАЦИЯ О ВЫЗОВЕ
        JPanel rightPanel = new JPanel(new BorderLayout());

        JPanel callInfoPanel = new JPanel(new BorderLayout());
        callInfoPanel.setBorder(BorderFactory.createTitledBorder("🎙️ ТЕКУЩИЙ ВЫЗОВ"));

        statusLabel = new JLabel("⚪ НЕ В ВЫЗОВЕ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        callInfoPanel.add(statusLabel, BorderLayout.NORTH);

        callTimerLabel = new JLabel("00:00:00", SwingConstants.CENTER);
        callTimerLabel.setFont(new Font("Arial", Font.BOLD, 40));
        callInfoPanel.add(callTimerLabel, BorderLayout.CENTER);

        rightPanel.add(callInfoPanel, BorderLayout.NORTH);

        statusArea = new JTextArea(12, 30);
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(BorderFactory.createTitledBorder("📝 ЛОГ СОБЫТИЙ"));
        rightPanel.add(statusScroll, BorderLayout.CENTER);

        centerSplit.setRightComponent(rightPanel);
        frame.add(centerSplit, BorderLayout.CENTER);

        // ========== НИЖНЯЯ ПАНЕЛЬ - КНОПКИ ВЫЗОВА ==========
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBorder(BorderFactory.createTitledBorder("🎮 УПРАВЛЕНИЕ ВЫЗОВОМ"));

        callBtn = new JButton("📞 ПОЗВОНИТЬ ВЫБРАННОМУ");
        callBtn.setEnabled(false);
        callBtn.setFont(new Font("Arial", Font.BOLD, 14));
        callBtn.setBackground(new Color(76, 175, 80));
        callBtn.setForeground(Color.WHITE);
        callBtn.setPreferredSize(new Dimension(200, 45));
        callBtn.addActionListener(e -> callSelectedContact());
        buttonPanel.add(callBtn);

        endCallBtn = new JButton("🔴 ЗАВЕРШИТЬ ЗВОНОК");
        endCallBtn.setEnabled(false);
        endCallBtn.setFont(new Font("Arial", Font.BOLD, 14));
        endCallBtn.setBackground(new Color(244, 67, 54));
        endCallBtn.setForeground(Color.WHITE);
        endCallBtn.setPreferredSize(new Dimension(200, 45));
        endCallBtn.addActionListener(e -> endCall());
        buttonPanel.add(endCallBtn);

        redialBtn = new JButton("🔄 ПЕРЕЗВОНИТЬ");
        redialBtn.setEnabled(false);
        redialBtn.setFont(new Font("Arial", Font.BOLD, 14));
        redialBtn.setBackground(new Color(255, 152, 0));
        redialBtn.setForeground(Color.WHITE);
        redialBtn.setPreferredSize(new Dimension(180, 45));
        redialBtn.addActionListener(e -> redial());
        buttonPanel.add(redialBtn);

        muteBtn = new JButton("🎤 MUTE");
        muteBtn.setEnabled(false);
        muteBtn.setFont(new Font("Arial", Font.BOLD, 14));
        muteBtn.setPreferredSize(new Dimension(120, 45));
        muteBtn.addActionListener(e -> toggleMute());
        buttonPanel.add(muteBtn);

        frame.add(buttonPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void applySettings() {
        nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            nickname = "Пользователь";
        }

        try {
            tcpPort = Integer.parseInt(tcpPortField.getText().trim());
            udpPort = tcpPort + 1;
        } catch (NumberFormatException e) {
            log("❌ Ошибка: неверный порт");
            JOptionPane.showMessageDialog(frame, "Введите корректный номер порта!", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        myInfoLabel.setText("✅ МОИ ДАННЫЕ: ник='" + nickname + "', IP=" + localAddress.getHostAddress() +
                ", TCP порт=" + tcpPort + ", UDP порт=" + udpPort);
        myInfoLabel.setForeground(new Color(76, 175, 80));

        frame.setTitle("P2P VoIP - " + nickname + " (порт " + tcpPort + ")");

        log("========================================");
        log("✅ Мои данные сохранены:");
        log("   Ник: " + nickname);
        log("   IP: " + localAddress.getHostAddress());
        log("   TCP порт: " + tcpPort);
        log("   UDP порт: " + udpPort);
        log("========================================");

        JOptionPane.showMessageDialog(frame,
                "✅ Данные сохранены!\n\n" +
                        "Ник: " + nickname + "\n" +
                        "IP: " + localAddress.getHostAddress() + "\n" +
                        "TCP порт: " + tcpPort + "\n" +
                        "UDP порт: " + udpPort + "\n\n" +
                        "👉 Теперь нажмите 'ЗАПУСТИТЬ ОЖИДАНИЕ'",
                "Настройки применены",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void addManualContact() {
        String name = contactNameField.getText().trim();
        String ip = contactIpField.getText().trim();
        String portStr = contactPortField.getText().trim();

        if (name.isEmpty() || ip.isEmpty() || portStr.isEmpty()) {
            log("❌ Заполните все поля для добавления контакта");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            log("❌ Неверный порт");
            return;
        }

        // Не добавляем себя
        if (name.equals(nickname) && ip.equals(localAddress.getHostAddress()) && port == tcpPort) {
            log("❌ Нельзя добавить самого себя");
            return;
        }

        PeerInfo peer = new PeerInfo(name, ip, port);
        peer.updateSeen();

        if (!discoveredPeers.containsKey(name)) {
            discoveredPeers.put(name, peer);
            SwingUtilities.invokeLater(() -> {
                peerListModel.addElement(name);
            });
            log("✅ Контакт добавлен: " + name + " (" + ip + ":" + port + ")");
        } else {
            log("⚠️ Контакт " + name + " уже существует");
        }
    }

    private void deleteSelectedContact() {
        String selected = peerList.getSelectedValue();
        if (selected == null) {
            log("❌ Выберите контакт для удаления");
            return;
        }
        discoveredPeers.remove(selected);
        SwingUtilities.invokeLater(() -> peerListModel.removeElement(selected));
        log("❌ Контакт удалён: " + selected);
    }

    private void callSelectedContact() {
        if (protocolHandler == null) {
            log("❌ Сначала нажмите 'ЗАПУСТИТЬ ОЖИДАНИЕ'!");
            JOptionPane.showMessageDialog(frame,
                    "Сначала нажмите кнопку 'ЗАПУСТИТЬ ОЖИДАНИЕ'!",
                    "Ошибка",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selected = peerList.getSelectedValue();
        if (selected == null) {
            log("❌ Выберите контакт из списка");
            return;
        }

        PeerInfo peer = discoveredPeers.get(selected);
        if (peer == null) {
            log("❌ Контакт не найден");
            return;
        }

        log("📞 Звонок: " + peer.nickname + " (" + peer.ip + ":" + peer.tcpPort + ")");
        callManager.initiateCall(peer);
    }

    private void startListening() {
        if (isListening) {
            log("Сервер уже запущен");
            return;
        }

        if (tcpPort == 0) {
            log("❌ Сначала нажмите 'ПРИМЕНИТЬ'!");
            JOptionPane.showMessageDialog(frame, "Сначала нажмите 'ПРИМЕНИТЬ'!", "Ошибка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            protocolHandler = new ProtocolHandler(tcpPort, this);
            protocolHandler.start();
            isListening = true;
            startListenBtn.setEnabled(false);
            startListenBtn.setText("✅ ОЖИДАНИЕ ЗАПУЩЕНО");
            log("🟢 СЕРВЕР ЗАПУЩЕН на порту " + tcpPort + "! Можно принимать звонки.");
        } catch (Exception e) {
            log("❌ Ошибка запуска сервера: " + e.getMessage());
        }
    }

    private void startDiscovery() {
        if (nickname == null || tcpPort == 0) {
            log("❌ Сначала нажмите 'ПРИМЕНИТЬ'!");
            return;
        }

        if (lanDiscovery != null) lanDiscovery.stop();

        lanDiscovery = new LanDiscovery(nickname, localAddress.getHostAddress(), tcpPort, this);
        lanDiscovery.start();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() { checkHeartbeats(); }
        }, 0, HEARTBEAT_INTERVAL);

        log("🔍 Запущен автопоиск в локальной сети");
    }

    private void refreshPeerList() {
        peerListModel.clear();
        for (String nick : discoveredPeers.keySet()) {
            peerListModel.addElement(nick);
        }
        log("🔄 Список обновлён: " + discoveredPeers.size() + " контактов");
    }

    private void endCall() {
        if (currentCallPeer != null) {
            callManager.endCall();
        }
    }

    private void redial() {
        if (currentCallPeer != null) {
            log("🔄 Перезвон на " + currentCallPeer.nickname);
            callManager.initiateCall(currentCallPeer);
        } else {
            PeerInfo last = callManager.getLastCalledPeer();
            if (last != null) {
                log("🔄 Перезвон на " + last.nickname);
                callManager.initiateCall(last);
            } else {
                log("❌ Нет сохранённого собеседника");
            }
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;
        audioManager.setMuted(isMuted);
        muteBtn.setText(isMuted ? "🎤 UNMUTE" : "🎤 MUTE");
        log(isMuted ? "🔇 Микрофон выключен" : "🎙️ Микрофон включён");
    }

    private void checkHeartbeats() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, PeerInfo> e : discoveredPeers.entrySet()) {
            if (now - e.getValue().lastSeen > HEARTBEAT_TIMEOUT) toRemove.add(e.getKey());
        }
        for (String nick : toRemove) {
            discoveredPeers.remove(nick);
            SwingUtilities.invokeLater(() -> peerListModel.removeElement(nick));
            log("⚠️ " + nick + " вышел из сети");
        }
    }

    public void onPeerDiscovered(PeerInfo peer) {
        if (peer.nickname.equals(nickname)) return;
        if (!discoveredPeers.containsKey(peer.nickname)) {
            discoveredPeers.put(peer.nickname, peer);
            SwingUtilities.invokeLater(() -> {
                if (!peerListModel.contains(peer.nickname)) {
                    peerListModel.addElement(peer.nickname);
                    log("🔍 Найден: " + peer.nickname + " (" + peer.ip + ":" + peer.tcpPort + ")");
                }
            });
        }
    }

    public void onIncomingCall(PeerInfo caller) {
        SwingUtilities.invokeLater(() -> {
            int option = JOptionPane.showConfirmDialog(frame,
                    "📞 " + caller.nickname + " ЗВОНИТ ВАМ!\n" +
                            "   IP: " + caller.ip + "\n" +
                            "   Порт: " + caller.tcpPort + "\n\n" +
                            "Принять звонок?",
                    "Входящий вызов",
                    JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.YES_OPTION) {
                acceptCall(caller);
            } else {
                if (protocolHandler != null) {
                    protocolHandler.sendCommand(caller.tcpSocket, "CALL_REJECTED");
                }
                log("❌ Звонок от " + caller.nickname + " отклонён");
            }
        });
    }

    private void acceptCall(PeerInfo caller) {
        try {
            currentCallPeer = caller;
            callManager.acceptCall(caller);
            startCallTimer();
            statusLabel.setText("🎙️ В РАЗГОВОРЕ С " + caller.nickname);
            callBtn.setEnabled(false);
            endCallBtn.setEnabled(true);
            redialBtn.setEnabled(false);
            muteBtn.setEnabled(true);
            audioManager.startAudioStream(caller.ip, udpPort, caller.udpPort);
            log("✅ Начат разговор с " + caller.nickname);
        } catch (Exception e) {
            log("❌ Ошибка: " + e.getMessage());
        }
    }

    public void onCallAccepted(PeerInfo peer, int peerUdpPort) {
        currentCallPeer = peer;
        peer.udpPort = peerUdpPort;
        SwingUtilities.invokeLater(() -> {
            startCallTimer();
            statusLabel.setText("🎙️ В РАЗГОВОРЕ С " + peer.nickname);
            callBtn.setEnabled(false);
            endCallBtn.setEnabled(true);
            muteBtn.setEnabled(true);
        });
        try {
            audioManager.startAudioStream(peer.ip, udpPort, peer.udpPort);
            log("✅ Аудиопоток запущен с " + peer.nickname);
        } catch (Exception e) {
            log("❌ Ошибка аудио: " + e.getMessage());
        }
    }

    public void onCallRejected(PeerInfo peer) {
        callManager.cancelRedial();
        SwingUtilities.invokeLater(() -> {
            log("❌ " + peer.nickname + " отклонил вызов");
            if (callManager.shouldRetry(peer)) {
                log("⏳ Повтор через 10 сек... (попытка " + (peer.retryCount + 1) + "/3)");
                callManager.scheduleRedial(peer);
            } else {
                log("❌ Превышено количество попыток для " + peer.nickname);
            }
        });
    }

    public void onCallEnded() {
        stopCall();
        log("🔴 Разговор завершён");
    }

    public void onCallFailed(PeerInfo peer, String reason) {
        log("❌ Ошибка вызова: " + reason);
        stopCall();
    }

    private void stopCall() {
        stopCallTimer();
        audioManager.stopAudioStream();
        currentCallPeer = null;
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("⚪ НЕ В ВЫЗОВЕ");
            callTimerLabel.setText("00:00:00");
            callBtn.setEnabled(true);
            endCallBtn.setEnabled(false);
            redialBtn.setEnabled(true);
            muteBtn.setEnabled(false);
            muteBtn.setText("🎤 MUTE");
            isMuted = false;
        });
    }

    private void startCallTimer() {
        callDurationSeconds = 0;
        callTimer = new javax.swing.Timer(1000, e -> {
            callDurationSeconds++;
            int hours = callDurationSeconds / 3600;
            int minutes = (callDurationSeconds % 3600) / 60;
            int seconds = callDurationSeconds % 60;
            callTimerLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        });
        callTimer.start();
    }

    private void stopCallTimer() {
        if (callTimer != null) {
            callTimer.stop();
            callTimer = null;
        }
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
        System.out.println(msg);
    }

    public int getUdpPort() { return udpPort; }
    public String getNickname() { return nickname; }
    public ProtocolHandler getProtocolHandler() { return protocolHandler; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new P2PNode());
    }
}