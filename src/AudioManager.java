import javax.sound.sampled.*;
import java.io.*;
import java.net.*;

public class AudioManager {
    private static final int BUFFER_SIZE = 4096;

    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private DatagramSocket udpSocket;
    private volatile boolean isRecording = false;
    private volatile boolean isPlaying = false;
    private volatile boolean isMuted = false;

    private InetAddress remoteAddress;
    private int localPort;
    private int remotePort;
    private AudioFormat currentFormat;

    private Thread sendThread;
    private Thread receiveThread;

    public void startAudioStream(String remoteIp, int localUdpPort, int remoteUdpPort) throws Exception {
        this.localPort = localUdpPort;
        this.remotePort = remoteUdpPort;
        this.remoteAddress = InetAddress.getByName(remoteIp);

        // Получаем поддерживаемый аудиоформат
        currentFormat = getSupportedAudioFormat();
        System.out.println("Используемый аудиоформат: " + currentFormat);

        // Настройка микрофона
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, currentFormat);
        if (!AudioSystem.isLineSupported(targetInfo)) {
            throw new LineUnavailableException("Микрофон не поддерживает формат: " + currentFormat);
        }
        microphone = (TargetDataLine) AudioSystem.getLine(targetInfo);
        microphone.open(currentFormat);

        // Настройка динамиков
        DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, currentFormat);
        if (!AudioSystem.isLineSupported(sourceInfo)) {
            throw new LineUnavailableException("Динамики не поддерживают формат: " + currentFormat);
        }
        speaker = (SourceDataLine) AudioSystem.getLine(sourceInfo);
        speaker.open(currentFormat);
        speaker.start();

        // UDP сокет
        udpSocket = new DatagramSocket(localPort);
        udpSocket.setSoTimeout(100);

        isRecording = true;
        isPlaying = true;

        sendThread = new Thread(this::sendAudio);
        receiveThread = new Thread(this::receiveAudio);
        sendThread.start();
        receiveThread.start();

        System.out.println("Аудиопоток запущен: локальный порт=" + localPort +
                ", удалённый=" + remoteIp + ":" + remoteUdpPort);
    }

    private AudioFormat getSupportedAudioFormat() {
        // Пробуем разные форматы
        int[] sampleRates = {44100, 48000, 22050, 16000, 11025, 8000};
        int[] sampleSizes = {16, 8};
        int[] channels = {1, 2};

        for (int rate : sampleRates) {
            for (int size : sampleSizes) {
                for (int ch : channels) {
                    AudioFormat format = new AudioFormat(rate, size, ch, true, false);
                    DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
                    DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);

                    if (AudioSystem.isLineSupported(targetInfo) &&
                            AudioSystem.isLineSupported(sourceInfo)) {
                        return format;
                    }
                }
            }
        }

        // Формат по умолчанию
        return new AudioFormat(44100, 16, 1, true, false);
    }

    private void sendAudio() {
        microphone.start();
        byte[] buffer = new byte[BUFFER_SIZE];

        while (isRecording) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);

            if (bytesRead > 0 && !isMuted) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, remoteAddress, remotePort);
                    udpSocket.send(packet);
                } catch (IOException e) {
                    if (isRecording) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }

        microphone.stop();
    }

    private void receiveAudio() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (isPlaying) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                speaker.write(packet.getData(), 0, packet.getLength());
            } catch (SocketTimeoutException e) {
                // Таймаут, продолжаем
            } catch (IOException e) {
                if (isPlaying) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
    }

    public void stopAudioStream() {
        isRecording = false;
        isPlaying = false;

        if (sendThread != null) {
            sendThread.interrupt();
            try { sendThread.join(1000); } catch (InterruptedException e) {}
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
            try { receiveThread.join(1000); } catch (InterruptedException e) {}
        }

        if (microphone != null) {
            microphone.close();
        }
        if (speaker != null) {
            speaker.stop();
            speaker.close();
        }
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }
}