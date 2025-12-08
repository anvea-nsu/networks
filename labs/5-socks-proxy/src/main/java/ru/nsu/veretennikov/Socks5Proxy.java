package ru.nsu.veretennikov;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class Socks5Proxy {

    private Selector selector;
    private DatagramChannel dnsChannel;
    private Map<Integer, Connection> dnsQueries = new HashMap<>();
    private int dnsQueryId = 1;

    // DNS сервер Google
    private static final InetSocketAddress DNS_SERVER = new InetSocketAddress("8.8.8.8", 53);

    enum State {
        GREETING,           // Читаем приветствие клиента
        GREETING_RESPONSE,  // Отправляем ответ на приветствие
        REQUEST,            // Читаем запрос на подключение
        RESOLVING,          // Ждём DNS ответа
        CONNECTING,         // Подключаемся к целевому серверу
        RESPONSE,           // Отправляем ответ клиенту
        RELAY               // Перенаправляем данные
    }

    class Connection {
        State state = State.GREETING;
        SocketChannel clientChannel;
        SocketChannel targetChannel;
        ByteBuffer clientBuffer = ByteBuffer.allocate(8192);
        ByteBuffer targetBuffer = ByteBuffer.allocate(8192);
        ByteBuffer clientToTarget = ByteBuffer.allocate(65536);
        ByteBuffer targetToClient = ByteBuffer.allocate(65536);

        String targetHost;
        int targetPort;
        InetAddress targetAddress;

        boolean clientClosed = false;
        boolean targetClosed = false;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Socks5Proxy <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        try {
            new Socks5Proxy().start(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start(int port) throws IOException {
        selector = Selector.open();

        // Создаём серверный сокет
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Создаём UDP сокет для DNS
        dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.register(selector, SelectionKey.OP_READ);

        System.out.println("SOCKS5 proxy started on port " + port);

        while (true) {
            selector.select();

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (!key.isValid()) continue;

                try {
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.channel() == dnsChannel) {
                        handleDnsResponse();
                    } else if (key.isConnectable()) {
                        handleConnect(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (IOException e) {
                    closeConnection((Connection) key.attachment());
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);

        Connection conn = new Connection();
        conn.clientChannel = clientChannel;

        clientChannel.register(selector, SelectionKey.OP_READ, conn);
    }

    private void handleRead(SelectionKey key) throws IOException {
        Connection conn = (Connection) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        if (channel == conn.clientChannel) {
            handleClientRead(conn, key);
        } else {
            handleTargetRead(conn, key);
        }
    }

    private void handleClientRead(Connection conn, SelectionKey key) throws IOException {
        int bytesRead = conn.clientChannel.read(conn.clientBuffer);

        if (bytesRead == -1) {
            conn.clientClosed = true;
            if (conn.state == State.RELAY && conn.targetChannel != null) {
                if (conn.clientToTarget.position() == 0) {
                    conn.targetChannel.shutdownOutput();
                }
            } else {
                closeConnection(conn);
            }
            return;
        }

        if (bytesRead == 0) return;

        switch (conn.state) {
            case GREETING:
                processGreeting(conn, key);
                break;
            case REQUEST:
                processRequest(conn, key);
                break;
            case RELAY:
                relayClientData(conn, key);
                break;
        }
    }

    private void handleTargetRead(Connection conn, SelectionKey key) throws IOException {
        int bytesRead = conn.targetChannel.read(conn.targetBuffer);

        if (bytesRead == -1) {
            conn.targetClosed = true;
            if (conn.targetToClient.position() == 0) {
                conn.clientChannel.shutdownOutput();
            }
            return;
        }

        if (bytesRead == 0) return;

        relayTargetData(conn, key);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        Connection conn = (Connection) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        if (channel == conn.clientChannel) {
            handleClientWrite(conn, key);
        } else {
            handleTargetWrite(conn, key);
        }
    }

    private void handleClientWrite(Connection conn, SelectionKey key) throws IOException {
        if (conn.targetToClient.position() > 0) {
            conn.targetToClient.flip();
            conn.clientChannel.write(conn.targetToClient);
            conn.targetToClient.compact();

            if (conn.targetToClient.position() == 0) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

                if (conn.targetClosed) {
                    conn.clientChannel.shutdownOutput();
                }

                // Возобновляем чтение от сервера
                if (conn.targetChannel != null) {
                    SelectionKey targetKey = conn.targetChannel.keyFor(selector);
                    if (targetKey != null && targetKey.isValid()) {
                        targetKey.interestOps(targetKey.interestOps() | SelectionKey.OP_READ);
                    }
                }
            }
        }
    }

    private void handleTargetWrite(Connection conn, SelectionKey key) throws IOException {
        if (conn.clientToTarget.position() > 0) {
            conn.clientToTarget.flip();
            conn.targetChannel.write(conn.clientToTarget);
            conn.clientToTarget.compact();

            if (conn.clientToTarget.position() == 0) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

                if (conn.clientClosed) {
                    conn.targetChannel.shutdownOutput();
                }

                // Возобновляем чтение от клиента
                SelectionKey clientKey = conn.clientChannel.keyFor(selector);
                if (clientKey != null && clientKey.isValid()) {
                    clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_READ);
                }
            }
        }
    }

    private void processGreeting(Connection conn, SelectionKey key) throws IOException {
        conn.clientBuffer.flip();

        if (conn.clientBuffer.remaining() < 2) {
            conn.clientBuffer.compact();
            return;
        }

        byte version = conn.clientBuffer.get();
        byte nMethods = conn.clientBuffer.get();

        if (version != 0x05) {
            closeConnection(conn);
            return;
        }

        if (conn.clientBuffer.remaining() < nMethods) {
            conn.clientBuffer.compact();
            return;
        }

        boolean noAuthFound = false;
        for (int i = 0; i < nMethods; i++) {
            if (conn.clientBuffer.get() == 0x00) {
                noAuthFound = true;
            }
        }

        conn.clientBuffer.compact();

        ByteBuffer response = ByteBuffer.allocate(2);
        response.put((byte) 0x05);
        response.put(noAuthFound ? (byte) 0x00 : (byte) 0xFF);
        response.flip();

        conn.clientChannel.write(response);

        if (!noAuthFound) {
            closeConnection(conn);
            return;
        }

        conn.state = State.REQUEST;
    }

    private void processRequest(Connection conn, SelectionKey key) throws IOException {
        conn.clientBuffer.flip();

        if (conn.clientBuffer.remaining() < 4) {
            conn.clientBuffer.compact();
            return;
        }

        byte version = conn.clientBuffer.get();
        byte cmd = conn.clientBuffer.get();
        conn.clientBuffer.get(); // RSV
        byte atyp = conn.clientBuffer.get();

        if (version != 0x05 || cmd != 0x01) {
            sendErrorResponse(conn, (byte) 0x07);
            return;
        }

        if (atyp == 0x01) { // IPv4
            if (conn.clientBuffer.remaining() < 6) {
                conn.clientBuffer.position(0);
                conn.clientBuffer.compact();
                return;
            }

            byte[] addr = new byte[4];
            conn.clientBuffer.get(addr);
            conn.targetPort = conn.clientBuffer.getShort() & 0xFFFF;
            conn.targetAddress = InetAddress.getByAddress(addr);

            conn.clientBuffer.compact();
            connectToTarget(conn, key);

        } else if (atyp == 0x03) { // Domain name
            if (conn.clientBuffer.remaining() < 1) {
                conn.clientBuffer.position(0);
                conn.clientBuffer.compact();
                return;
            }

            int len = conn.clientBuffer.get() & 0xFF;

            if (conn.clientBuffer.remaining() < len + 2) {
                conn.clientBuffer.position(0);
                conn.clientBuffer.compact();
                return;
            }

            byte[] domainBytes = new byte[len];
            conn.clientBuffer.get(domainBytes);
            conn.targetHost = new String(domainBytes);
            conn.targetPort = conn.clientBuffer.getShort() & 0xFFFF;

            conn.clientBuffer.compact();

            // Отправляем DNS запрос
            conn.state = State.RESOLVING;
            sendDnsQuery(conn, conn.targetHost);

        } else {
            sendErrorResponse(conn, (byte) 0x08);
        }
    }

    private void sendDnsQuery(Connection conn, String hostname) throws IOException {
        int queryId = dnsQueryId++;
        dnsQueries.put(queryId, conn);

        ByteBuffer query = ByteBuffer.allocate(512);

        // Header
        query.putShort((short) queryId);
        query.putShort((short) 0x0100); // Standard query
        query.putShort((short) 1); // Questions
        query.putShort((short) 0); // Answer RRs
        query.putShort((short) 0); // Authority RRs
        query.putShort((short) 0); // Additional RRs

        // Question
        String[] labels = hostname.split("\\.");
        for (String label : labels) {
            query.put((byte) label.length());
            query.put(label.getBytes());
        }
        query.put((byte) 0); // End of name

        query.putShort((short) 1); // Type A
        query.putShort((short) 1); // Class IN

        query.flip();
        dnsChannel.send(query, DNS_SERVER);
    }

    private void handleDnsResponse() throws IOException {
        ByteBuffer response = ByteBuffer.allocate(512);
        dnsChannel.receive(response);
        response.flip();

        if (response.remaining() < 12) return;

        int queryId = response.getShort() & 0xFFFF;
        response.getShort(); // Flags
        response.getShort(); // Questions
        int answers = response.getShort() & 0xFFFF;
        response.getShort(); // Authority RRs
        response.getShort(); // Additional RRs

        Connection conn = dnsQueries.remove(queryId);
        if (conn == null) return;

        // Skip question section
        while (response.hasRemaining() && response.get() != 0) {}
        if (response.remaining() < 4) {
            sendErrorResponse(conn, (byte) 0x04);
            return;
        }
        response.getShort(); // Type
        response.getShort(); // Class

        // Parse answer section
        InetAddress address = null;
        for (int i = 0; i < answers && response.remaining() >= 12; i++) {
            // Skip name (compression pointer)
            if ((response.get(response.position()) & 0xC0) == 0xC0) {
                response.getShort();
            } else {
                while (response.hasRemaining() && response.get() != 0) {}
            }

            int type = response.getShort() & 0xFFFF;
            response.getShort(); // Class
            response.getInt(); // TTL
            int dataLen = response.getShort() & 0xFFFF;

            if (type == 1 && dataLen == 4) { // A record
                byte[] addr = new byte[4];
                response.get(addr);
                address = InetAddress.getByAddress(addr);
                break;
            } else {
                response.position(response.position() + dataLen);
            }
        }

        if (address == null) {
            sendErrorResponse(conn, (byte) 0x04);
            return;
        }

        conn.targetAddress = address;
        SelectionKey clientKey = conn.clientChannel.keyFor(selector);
        connectToTarget(conn, clientKey);
    }

    private void connectToTarget(Connection conn, SelectionKey clientKey) throws IOException {
        conn.targetChannel = SocketChannel.open();
        conn.targetChannel.configureBlocking(false);

        boolean connected = conn.targetChannel.connect(
                new InetSocketAddress(conn.targetAddress, conn.targetPort)
        );

        conn.state = State.CONNECTING;

        if (connected) {
            handleConnect(conn.targetChannel.register(selector, SelectionKey.OP_WRITE, conn));
        } else {
            conn.targetChannel.register(selector, SelectionKey.OP_CONNECT, conn);
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        Connection conn = (Connection) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        if (!channel.finishConnect()) {
            return;
        }

        // Отправляем успешный ответ клиенту
        ByteBuffer response = ByteBuffer.allocate(10);
        response.put((byte) 0x05); // Version
        response.put((byte) 0x00); // Success
        response.put((byte) 0x00); // Reserved
        response.put((byte) 0x01); // IPv4
        response.putInt(0); // Bind address (0.0.0.0)
        response.putShort((short) 0); // Bind port (0)
        response.flip();

        conn.clientChannel.write(response);

        conn.state = State.RELAY;

        // Регистрируем оба канала на чтение
        SelectionKey clientKey = conn.clientChannel.keyFor(selector);
        clientKey.interestOps(SelectionKey.OP_READ);
        key.interestOps(SelectionKey.OP_READ);
    }

    private void relayClientData(Connection conn, SelectionKey key) throws IOException {
        conn.clientBuffer.flip();

        if (conn.clientToTarget.remaining() < conn.clientBuffer.remaining()) {
            // Буфер заполнен, останавливаем чтение от клиента
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        }

        conn.clientToTarget.put(conn.clientBuffer);
        conn.clientBuffer.clear();

        // Включаем запись к серверу
        SelectionKey targetKey = conn.targetChannel.keyFor(selector);
        if (targetKey != null && targetKey.isValid()) {
            targetKey.interestOps(targetKey.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    private void relayTargetData(Connection conn, SelectionKey key) throws IOException {
        conn.targetBuffer.flip();

        if (conn.targetToClient.remaining() < conn.targetBuffer.remaining()) {
            // Буфер заполнен, останавливаем чтение от сервера
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        }

        conn.targetToClient.put(conn.targetBuffer);
        conn.targetBuffer.clear();

        // Включаем запись к клиенту
        SelectionKey clientKey = conn.clientChannel.keyFor(selector);
        if (clientKey != null && clientKey.isValid()) {
            clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    private void sendErrorResponse(Connection conn, byte error) throws IOException {
        ByteBuffer response = ByteBuffer.allocate(10);
        response.put((byte) 0x05);
        response.put(error);
        response.put((byte) 0x00);
        response.put((byte) 0x01);
        response.putInt(0);
        response.putShort((short) 0);
        response.flip();

        conn.clientChannel.write(response);
        closeConnection(conn);
    }

    private void closeConnection(Connection conn) {
        if (conn == null) return;

        try {
            if (conn.clientChannel != null) {
                SelectionKey key = conn.clientChannel.keyFor(selector);
                if (key != null) key.cancel();
                conn.clientChannel.close();
            }

            if (conn.targetChannel != null) {
                SelectionKey key = conn.targetChannel.keyFor(selector);
                if (key != null) key.cancel();
                conn.targetChannel.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}