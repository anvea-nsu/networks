package org.example.snake.messages;

import org.example.snake.game.GameManager;
import org.example.snake.protocol.SnakesProto;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class NetworkManager {
    private static final String MULTICAST_ADDRESS = "239.192.0.4";
    private static final int MULTICAST_PORT = 9192;

    private final DatagramSocket unicastSocket;
    private final MulticastSocket multicastSocket;
    private final InetAddress multicastGroup;
    private final ConcurrentHashMap<Integer, InetSocketAddress> playerAddresses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PendingMessage> pendingMessages = new ConcurrentHashMap<>();

    private final ScheduledExecutorService receiverScheduler;
    private volatile boolean running = false;
    private GameManager gameManager;

    public NetworkManager() throws IOException {
        this.unicastSocket = new DatagramSocket();
        this.unicastSocket.setSoTimeout(100);

        this.multicastSocket = new MulticastSocket(MULTICAST_PORT);
        this.multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);
        var net = findNetworkInterface();
        multicastSocket.setNetworkInterface(net);
        this.multicastSocket.joinGroup(new InetSocketAddress(multicastGroup, MULTICAST_PORT), net);
        this.multicastSocket.setSoTimeout(1000);

        this.receiverScheduler = Executors.newScheduledThreadPool(2);
    }

    private NetworkInterface findNetworkInterface() throws SocketException {
        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        List<NetworkInterface> suitableInterfaces = new ArrayList<>();

        System.out.println("Available network interfaces:");
        for (NetworkInterface iface : interfaces) {
            System.out.println("  - " + iface.getName() + ": " + iface.getDisplayName() +
                    " (up=" + iface.isUp() +
                    ", loopback=" + iface.isLoopback() +
                    ", multicast=" + iface.supportsMulticast() + ")");

            if (iface.isUp() && !iface.isLoopback() && iface.supportsMulticast()) {
                boolean hasIPv4 = false;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        hasIPv4 = true;
                        System.out.println("    IPv4: " + addr.getHostAddress());
                        break;
                    }
                }

                if (hasIPv4) {
                    suitableInterfaces.add(iface);
                }
            }
        }

        if (suitableInterfaces.isEmpty()) {
            System.err.println("No suitable network interface found!");
            System.err.println("Trying to use any available interface...");

            for (NetworkInterface iface : interfaces) {
                if (iface.isUp() && iface.supportsMulticast()) {
                    System.err.println("Using fallback interface: " + iface.getName());
                    return iface;
                }
            }

            return null;
        }

        for (NetworkInterface iface : suitableInterfaces) {
            String name = iface.getName().toLowerCase();
            if (name.equals("en0") || name.equals("en1")) {
                System.out.println("Selected preferred interface: " + iface.getName());
                return iface;
            }
        }

        suitableInterfaces.sort(Comparator.comparing(NetworkInterface::getName));
        NetworkInterface selected = suitableInterfaces.get(0);
        System.out.println("Selected interface: " + selected.getName());
        return selected;
    }

    public void setGameManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void start() {
        running = true;
        startReceivers();
    }

    public void stop() {
        running = false;
        receiverScheduler.shutdown();
        unicastSocket.close();
        multicastSocket.close();
    }

    public void sendSteerMessage(SnakesProto.Direction direction, InetSocketAddress master, int playerId) {
        System.out.println("Sending STEER to " + master + " with playerId: " + playerId);
        SnakesProto.GameMessage message = MessageFactory.createSteerMessage(
                System.currentTimeMillis(), playerId, direction);
        sendUnicast(message, master);
        add(message, master);
    }

    public void sendJoinMessage(String playerName, String gameName, SnakesProto.NodeRole role, InetSocketAddress master) {
        System.out.println("Sending JOIN to " + master + " for game: " + gameName + " as " + role);
        SnakesProto.GameMessage message = MessageFactory.createJoinMessage(
                System.currentTimeMillis(), playerName, gameName, role);
        sendUnicast(message, master);
        add(message, master);
    }

    public void sendAck(long msgSeq, int senderId, int receiverId, InetSocketAddress address) {
        SnakesProto.GameMessage message = MessageFactory.createAckMessage(
                msgSeq, senderId, receiverId);
        sendUnicast(message, address);
    }

    private void sendUnicast(SnakesProto.GameMessage message, InetSocketAddress address) {
        try {
            System.out.println("Send " + message.getTypeCase() + " to " + address);
            byte[] data = message.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            unicastSocket.send(packet);
        } catch (IOException e) {
            System.out.println(message.getTypeCase() + " " + message.getReceiverId());
            System.err.println("Failed to send to " + address + ": " + e.getMessage());
        }
    }

    private void add(SnakesProto.GameMessage message, InetSocketAddress address) {
        pendingMessages.put(message.getMsgSeq(), new PendingMessage(message, address));
    }

    public void sendPing(InetSocketAddress master, int playerId){
        var message = MessageFactory.createPingMessage(
                System.currentTimeMillis(), playerId);
        sendUnicast(message, master);
        add(message, master);
    }

    public void broadcastState(SnakesProto.GameState state, int senderId) {
        SnakesProto.GameMessage message = MessageFactory.createStateMessage(
                System.currentTimeMillis(), senderId, state);

        for (var playerEntry : playerAddresses.entrySet()) {
            if (playerEntry.getKey() != senderId) {
                sendUnicast(message, playerEntry.getValue());
                add(message, playerEntry.getValue());
            }
        }
    }

    public void broadcastState(SnakesProto.GameState state, int senderId, Map<Integer, InetSocketAddress> playerAddresses) {
        SnakesProto.GameMessage message = MessageFactory.createStateMessage(
                System.currentTimeMillis(), senderId, state);

        for (var playerEntry : playerAddresses.entrySet()) {
            if (playerEntry.getKey() != senderId) {
                sendUnicast(message, playerEntry.getValue());
                add(message, playerEntry.getValue());
            }
        }
    }

    public void sendGameAnnouncement(SnakesProto.GameAnnouncement announcement) {
        SnakesProto.GameMessage message = MessageFactory.createAnnouncementMessage(
                System.currentTimeMillis(), announcement);
        sendMulticast(message);
    }

    public void sendGameAnnouncement(SnakesProto.GameAnnouncement announcement, InetSocketAddress address) {
        SnakesProto.GameMessage message = MessageFactory.createAnnouncementMessage(
                System.currentTimeMillis(), announcement);
        sendUnicast(message, address);
    }

    public void sendError(String errorMessage, InetSocketAddress address) {
        SnakesProto.GameMessage message = MessageFactory.createErrorMessage(
                System.currentTimeMillis(), 0, errorMessage);
        sendUnicast(message, address);
    }

    public InetSocketAddress getMyAddress() {
        try {
            return new InetSocketAddress(InetAddress.getLocalHost(), unicastSocket.getLocalPort());
        } catch (UnknownHostException e) {
            return new InetSocketAddress("127.0.0.1", unicastSocket.getLocalPort());
        }
    }

    private void startReceivers() {
        receiverScheduler.execute(this::receiveUnicast);
        receiverScheduler.execute(this::receiveMulticast);
    }

    private void receiveUnicast() {
        byte[] buffer = new byte[4096];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                unicastSocket.receive(packet);

                SnakesProto.GameMessage message = parseMessage(packet);

                if (message != null) {
                    InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    System.out.println("Received " + message.getTypeCase() + " from " + sender);
                    if (gameManager != null) {
                        gameManager.handleIncomingMessage(message, sender);
                    }
                }
            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                if (running) System.err.println("Unicast receive error: " + e.getMessage());
            }
        }
    }

    private void receiveMulticast() {
        byte[] buffer = new byte[4096];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);

                SnakesProto.GameMessage message = parseMessage(packet);
                if (message != null) {
                    InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());

                    if (isMyAddress(sender)) {
                        continue;
                    }

                    if (gameManager != null) {
                        gameManager.handleIncomingMessage(message, sender);
                    }
                }
            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                if (running) System.err.println("Multicast receive error: " + e.getMessage());
            }
        }
    }

    private boolean isMyAddress(InetSocketAddress address) {
        InetSocketAddress myAddress = getMyAddress();
        return address.getAddress().equals(myAddress.getAddress()) &&
                address.getPort() == myAddress.getPort();
    }

    private SnakesProto.GameMessage parseMessage(DatagramPacket packet) {
        try {
            return SnakesProto.GameMessage.parseFrom(
                    Arrays.copyOf(packet.getData(), packet.getLength()));
        } catch (Exception e) {
            System.err.println("Failed to parse message from " + packet.getAddress() + ": " + e.getMessage());
            return null;
        }
    }

    private void sendMulticast(SnakesProto.GameMessage message) {
        try {
            byte[] data = message.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length, multicastGroup, MULTICAST_PORT);
            unicastSocket.send(packet);
        } catch (IOException e) {
            System.err.println("Failed to send multicast: " + e.getMessage());
        }
    }

    public void resendMessage(long msgSeq) {
        PendingMessage pending = pendingMessages.get(msgSeq);
        if (pending != null) {
            resendMessage(msgSeq, pending.getAddress());
        }
    }

    public void resendMessage(long msgSeq, InetSocketAddress address) {
        PendingMessage pending = pendingMessages.get(msgSeq);
        if (pending != null) {
            System.out.println("Resending " + pending.getMessage().getTypeCase() + " to " + address);
            sendUnicast(pending.getMessage(), address);
            pending.update();
        }
    }

    public void sendDiscoverMessage() {
        SnakesProto.GameMessage message = MessageFactory.createDiscoverMessage(System.currentTimeMillis());
        sendMulticast(message);
    }

    public void sendRoleChangeMessage(int receiverId, InetSocketAddress address,
                                      SnakesProto.NodeRole senderRole, SnakesProto.NodeRole receiverRole) {
        SnakesProto.GameMessage message = MessageFactory.createRoleChangeMessage(
                System.currentTimeMillis(), 0, receiverId, senderRole, receiverRole);
        sendUnicast(message, address);
        add(message, address);
    }

    public void addPlayerAddress(int playerId, InetSocketAddress address) {
        playerAddresses.put(playerId, address);
        System.out.println("Added player " + playerId + " address: " + address);
    }

    public void removePlayerAddress(int playerId) {
        InetSocketAddress removed = playerAddresses.remove(playerId);
        System.out.println("Removed player " + playerId + " (address was: " + removed + ")");
    }

    public InetSocketAddress getPlayerAddress(int playerId) {
        return playerAddresses.get(playerId);
    }

    public ConcurrentHashMap<Long, PendingMessage> getPendingMessages() {
        return pendingMessages;
    }

    public void removeMessage(Long msgSeq){
        PendingMessage removed = pendingMessages.remove(msgSeq);
        if (removed != null) {
            System.out.println("Removed pending message: " + removed.getMessage().getTypeCase());
        }
    }
}