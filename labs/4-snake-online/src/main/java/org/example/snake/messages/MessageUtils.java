package org.example.snake.messages;

import org.example.snake.protocol.SnakesProto.GameMessage;

import java.io.IOException;

public class MessageUtils {
    public static byte[] serializeMessage(GameMessage message) {
        return message.toByteArray();
    }

    public static GameMessage deserializeMessage(byte[] data) throws IOException {
        return GameMessage.parseFrom(data);
    }

    public static GameMessage.Builder createBaseMessage(long msgSeq) {
        return GameMessage.newBuilder()
                .setMsgSeq(msgSeq);
    }
}
