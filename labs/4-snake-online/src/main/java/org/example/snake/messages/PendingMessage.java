package org.example.snake.messages;

import org.example.snake.protocol.SnakesProto;

import java.net.InetSocketAddress;

public class PendingMessage {
    private final SnakesProto.GameMessage message;
    private final InetSocketAddress address;
    private long sendTime;
    private int retryCount;

    PendingMessage(SnakesProto.GameMessage message, InetSocketAddress address){
        this.message = message;
        this.address = address;
        sendTime = System.currentTimeMillis();
        retryCount = 0;
    }

    public SnakesProto.GameMessage getMessage() {
        return message;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void update() {
        sendTime = System.currentTimeMillis();
        retryCount++;
    }
}
