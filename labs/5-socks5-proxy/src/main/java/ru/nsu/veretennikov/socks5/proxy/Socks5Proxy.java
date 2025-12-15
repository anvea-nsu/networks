package ru.nsu.veretennikov.socks5.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class Socks5Proxy {
    private Selector selector;
    private DatagramChannel dnsChannel;
    private Map<SelectionKey, ClientConnection> connections = new HashMap<>();
    private Map<Integer, ClientConnection> dnsRequests = new HashMap<>();
    private int dnsRequestId = 1;

    enum State {
        GREETING, REQUEST, CONNECTING, DNS_RESOLVING, TUNNELING
    }

    class ClientConnection {
        SocketChannel client;
        SocketChannel remote;
        State state = State.GREETING;
        ByteBuffer clientBuffer = ByteBuffer.allocate(8192);
        ByteBuffer remoteBuffer = ByteBuffer.allocate(8192);
        String targetHost;
        int targetPort;
        byte addressType;
        byte[] addressBytes;
        int dnsId;
        boolean clientClosed = false;
        boolean remoteClosed = false;
        boolean clientWritePending = false;
        boolean remoteWritePending = false;

        ClientConnection(SocketChannel client) {
            this.client = client;
        }

        void close() {
            try { if (client != null) client.close(); } catch (IOException e) {}
            try { if (remote != null) remote.close(); } catch (IOException e) {}
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Socks5Proxy <port>");
            System.exit(1);
        }
        try {
            new Socks5Proxy().start(Integer.parseInt(args[0]));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void start(int port) throws IOException {
        selector = Selector.open();

        dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.register(selector, SelectionKey.OP_READ);

        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.bind(new InetSocketAddress(port));
        server.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("SOCKS5 Proxy listening on port " + port);

        while (true) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) continue;

                try {
                    if (key.isAcceptable()) {
                        handleAccept((ServerSocketChannel) key.channel());
                    }
                    else if (key.channel() == dnsChannel) {
                        handleDnsResponse();
                    } else if (key.isConnectable()) {
                        handleConnect(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (Exception e) {
                    ClientConnection conn = connections.get(key);
                    if (conn != null) {
                        cleanupConnection(conn);
                    }
                }
            }
        }
    }

    void handleAccept(ServerSocketChannel server) throws IOException {
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        SelectionKey key = client.register(selector, SelectionKey.OP_READ);
        ClientConnection conn = new ClientConnection(client);
        connections.put(key, conn);
        System.out.println("New client connected: " + client.getRemoteAddress());
    }

    void handleRead(SelectionKey key) throws IOException {
        ClientConnection conn = connections.get(key);
        if (conn == null) return;

        SocketChannel channel = (SocketChannel) key.channel();

        if (channel == conn.client) {
            handleClientRead(key, conn);
        } else if (channel == conn.remote) {
            handleRemoteRead(key, conn);
        }
    }

    void handleClientRead(SelectionKey key, ClientConnection conn) throws IOException {
        int read = conn.client.read(conn.clientBuffer);

        // Ошибка чтения - клиент закрыл соединение.
        if (read == -1) {
            conn.clientClosed = true;
            if (conn.state == State.TUNNELING && conn.remote != null) {
                conn.remote.shutdownOutput(); // Закрывает только отправку данных.
                if (conn.remoteClosed) {
                    cleanupConnection(conn); // Полностью чистим соединение, только когда обе стороны закрыты.
                }
            } else {
                cleanupConnection(conn);
            }
            return;
        }

        if (read == 0) return;

        conn.clientBuffer.flip();

        switch (conn.state) {
            case GREETING:
                handleGreeting(key, conn);
                break;
            case REQUEST:
                handleRequest(key, conn);
                break;
            case TUNNELING:
                tunnelToRemote(key, conn);
                break;
        }
    }

    void handleRemoteRead(SelectionKey key, ClientConnection conn) throws IOException {
        int read = conn.remote.read(conn.remoteBuffer);

        if (read == -1) {
            conn.remoteClosed = true;
            conn.client.shutdownOutput();
            if (conn.clientClosed) {
                cleanupConnection(conn);
            }
            return;
        }

        if (read == 0) return;

        conn.remoteBuffer.flip();
        tunnelToClient(key, conn);
    }

    void handleGreeting(SelectionKey key, ClientConnection conn) throws IOException {
        if (conn.clientBuffer.remaining() < 2) {
            conn.clientBuffer.compact(); // переносит непрочитанные данные от position до limit в начало буфера.
            return;
        }

        byte version = conn.clientBuffer.get();     // VER
        byte nmethods = conn.clientBuffer.get();    // NMETHODS

        System.out.println("SOCKS version: " + version + ", methods: " + nmethods);

        if (version != 5 || conn.clientBuffer.remaining() < nmethods) {
            conn.clientBuffer.compact();
            return;
        }

        conn.clientBuffer.position(conn.clientBuffer.position() + nmethods); // пропускаем METHODS
        conn.clientBuffer.compact();

        ByteBuffer response = ByteBuffer.allocate(2);
        response.put((byte) 5); // VER
        response.put((byte) 0); // METHOD (NO AUTH)
        response.flip();
        conn.client.write(response);

        System.out.println("Sent greeting response");

        conn.state = State.REQUEST;
    }

    void handleRequest(SelectionKey key, ClientConnection conn) throws IOException {
        if (conn.clientBuffer.remaining() < 4) {
            conn.clientBuffer.compact();
            return;
        }

        conn.clientBuffer.mark();
        byte version = conn.clientBuffer.get();     // VER
        byte cmd = conn.clientBuffer.get();         // CMD
        conn.clientBuffer.get();                    // RSV
        conn.addressType = conn.clientBuffer.get(); // ATYP

        System.out.println("Request - cmd: " + cmd + ", addrType: " + conn.addressType);

        int addressLen = 0;
        if (conn.addressType == 1) {
            addressLen = 4;
        } else if (conn.addressType == 3) {
            if (conn.clientBuffer.remaining() < 1) {
                conn.clientBuffer.reset();
                conn.clientBuffer.compact();
                return;
            }
            addressLen = conn.clientBuffer.get() & 0xFF; // Расширяем до знакового int,
        } else {                                         // чтобы избежать проблем с отрицательными значениями.
            System.err.println("Unsupported address type: " + conn.addressType);
            sendError(conn, (byte) 8);
            return;
        }

        if (conn.clientBuffer.remaining() < addressLen + 2) { // + 2 для порта
            conn.clientBuffer.reset();
            conn.clientBuffer.compact();
            return;
        }

        conn.addressBytes = new byte[addressLen];
        conn.clientBuffer.get(conn.addressBytes);
        conn.targetPort = conn.clientBuffer.getShort() & 0xFFFF;
        conn.clientBuffer.compact();

        if (cmd != 1) {
            System.err.println("Unsupported command: " + cmd);
            sendError(conn, (byte) 7);
            return;
        }

        if (conn.addressType == 1) {
            InetAddress addr = InetAddress.getByAddress(conn.addressBytes);
            System.out.println("---Connecting to " + addr + ":" + conn.targetPort);
            connectToRemote(key, conn, addr);
        } else {
            conn.targetHost = new String(conn.addressBytes);
            System.out.println("---Resolving " + conn.targetHost + ":" + conn.targetPort);
            resolveDns(key, conn);
        }
    }

    void resolveDns(SelectionKey key, ClientConnection conn) throws IOException {
        conn.state = State.DNS_RESOLVING;
        conn.dnsId = dnsRequestId++;
        dnsRequests.put(conn.dnsId, conn);

        Message query = Message.newQuery(Record.newRecord(Name.fromString(conn.targetHost + "."), Type.A, DClass.IN));
        query.getHeader().setID(conn.dnsId);
        byte[] queryBytes = query.toWire(); // преобразование в байты

        ResolverConfig config = ResolverConfig.getCurrentConfig();  // читает DNS настройки системы
        InetSocketAddress dnsServer = config.servers().get(0);      // берем первый DNS сервер из списка
        System.out.println("Sending DNS query to " + dnsServer + " for " + conn.targetHost);
        dnsChannel.send(ByteBuffer.wrap(queryBytes), dnsServer);
    }

    void handleDnsResponse() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(512);
        dnsChannel.receive(buf);
        buf.flip();

        if (buf.remaining() == 0) return;

        Message response = new Message(buf.array());
        int id = response.getHeader().getID();

        System.out.println("DNS response received, id: " + id);

        ClientConnection conn = dnsRequests.remove(id);
        if (conn == null) {
            System.err.println("No connection found for DNS id: " + id);
            return;
        }

        Record[] answers = response.getSectionArray(Section.ANSWER);
        if (answers.length == 0) {
            System.err.println("No DNS answers");
            sendError(conn, (byte) 4);
            return;
        }

        for (Record answer : answers) {
            if (answer instanceof ARecord) { // перебираем все A-записи (IPv4 адреса).
                InetAddress addr = ((ARecord) answer).getAddress();
                System.out.println("Resolved " + conn.targetHost + " to " + addr);
                SelectionKey key = findKeyForConnection(conn);
                if (key != null) {
                    connectToRemote(key, conn, addr);
                }
                return;
            }
        }

        System.err.println("No A record found");
        sendError(conn, (byte) 4);
    }

    SelectionKey findKeyForConnection(ClientConnection conn) {
        for (Map.Entry<SelectionKey, ClientConnection> e : connections.entrySet()) {
            if (e.getValue() == conn && e.getKey().channel() == conn.client) {
                return e.getKey();
            }
        }
        return null;
    }

    void connectToRemote(SelectionKey key, ClientConnection conn, InetAddress addr) throws IOException {
        conn.remote = SocketChannel.open();
        conn.remote.configureBlocking(false);
        boolean connected = conn.remote.connect(new InetSocketAddress(addr, conn.targetPort));
        conn.state = State.CONNECTING;

        System.out.println("Initiating connection to " + addr + ":" + conn.targetPort + ", immediate: " + connected);

        if (connected) { // Соединение установлено сразу.
            finishConnection(key, conn);
        } else {
            conn.remote.register(selector, SelectionKey.OP_CONNECT, conn);
        }
    }

    void handleConnect(SelectionKey key) throws IOException {
        ClientConnection conn = (ClientConnection) key.attachment();
        SocketChannel remote = (SocketChannel) key.channel();

        System.out.println("Finishing connection...");

        if (!remote.finishConnect()) { // выдаст значение false, если процесс подключения еще не завершен.
            System.err.println("finishConnect returned false");
            return;
        }

        System.out.println("Connection established to remote");

        SelectionKey clientKey = findKeyForConnection(conn);
        finishConnection(clientKey, conn);
    }

    void finishConnection(SelectionKey clientKey, ClientConnection conn) throws IOException {
        SelectionKey remoteKey = conn.remote.register(selector, SelectionKey.OP_READ);
        connections.put(remoteKey, conn);

        ByteBuffer response = ByteBuffer.allocate(10);
        response.put((byte) 5);         // VER
        response.put((byte) 0);         // REP (код ответа - успех)
        response.put((byte) 0);         // RSV
        response.put((byte) 1);         // ATYP (IPv4)
        response.putInt(0);       // BND.ADDR
        response.putShort((short) 0);   // DND.PORT
        response.flip();
        conn.client.write(response);

        System.out.println("Sent success response, entering TUNNELING mode");

        conn.state = State.TUNNELING;

        if (conn.clientBuffer.position() > 0) {
            conn.clientBuffer.flip();
            tunnelToRemote(clientKey, conn);
        }
    }

    void tunnelToRemote(SelectionKey key, ClientConnection conn) throws IOException {
        if (!conn.clientBuffer.hasRemaining()) {
            conn.clientBuffer.compact();
            return;
        }

        int written = conn.remote.write(conn.clientBuffer);

        if (conn.clientBuffer.hasRemaining()) { // если остались непрочитанные данные, регистрируем интерес на запись.
            SelectionKey remoteKey = findRemoteKey(conn);
            if (remoteKey != null) {
                remoteKey.interestOps(remoteKey.interestOps() | SelectionKey.OP_WRITE);
                conn.remoteWritePending = true;
            }
        } else {
            conn.remoteWritePending = false;
        }

        conn.clientBuffer.compact();
    }

    void tunnelToClient(SelectionKey key, ClientConnection conn) throws IOException {
        if (!conn.remoteBuffer.hasRemaining()) {
            conn.remoteBuffer.compact();
            return;
        }

        int written = conn.client.write(conn.remoteBuffer);

        if (conn.remoteBuffer.hasRemaining()) { // если буфер отправлен не полностью, регистрируем интерес на запись.
            SelectionKey clientKey = findKeyForConnection(conn);
            if (clientKey != null) {
                clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_WRITE);
                conn.clientWritePending = true;
            }
        } else {
            conn.clientWritePending = false;
        }

        conn.remoteBuffer.compact();
    }

    SelectionKey findRemoteKey(ClientConnection conn) {
        for (Map.Entry<SelectionKey, ClientConnection> e : connections.entrySet()) {
            if (e.getValue() == conn && e.getKey().channel() == conn.remote) {
                return e.getKey();
            }
        }
        return null;
    }

    void handleWrite(SelectionKey key) throws IOException {
        ClientConnection conn = connections.get(key);
        if (conn == null) return;

        SocketChannel channel = (SocketChannel) key.channel();

        if (channel == conn.client) {
            if (!conn.clientWritePending) {
                key.interestOps(SelectionKey.OP_READ);
                return;
            }

            conn.remoteBuffer.flip();
            int written = conn.client.write(conn.remoteBuffer);

            if (!conn.remoteBuffer.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ);
                conn.clientWritePending = false;
            }
            conn.remoteBuffer.compact();
        } else if (channel == conn.remote) {
            if (!conn.remoteWritePending) {
                key.interestOps(SelectionKey.OP_READ);
                return;
            }

            conn.clientBuffer.flip();
            int written = conn.remote.write(conn.clientBuffer);

            if (!conn.clientBuffer.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ);
                conn.remoteWritePending = false;
            }
            conn.clientBuffer.compact();
        }
    }

    void sendError(ClientConnection conn, byte error) throws IOException {
        System.err.println("Sending error response: " + error);
        ByteBuffer response = ByteBuffer.allocate(10);
        response.put((byte) 5);
        response.put(error);
        response.put((byte) 0);
        response.put((byte) 1);
        response.putInt(0);
        response.putShort((short) 0);
        response.flip();
        conn.client.write(response);
        cleanupConnection(conn);
    }

    void cleanupConnection(ClientConnection conn) {
        conn.close();
        connections.entrySet().removeIf(e -> e.getValue() == conn);
    }
}