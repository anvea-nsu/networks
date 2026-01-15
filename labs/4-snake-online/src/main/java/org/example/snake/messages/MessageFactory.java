package org.example.snake.messages;

import org.example.snake.protocol.SnakesProto;

public class MessageFactory {

    public static SnakesProto.GameMessage createSteerMessage(
            long msgSeq, int senderId, SnakesProto.Direction direction) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setSenderId(senderId) // Указываем актуальный sender_id
                .setSteer(SnakesProto.GameMessage.SteerMsg.newBuilder()
                        .setDirection(direction)
                        .build())
                .build();
    }

    public static SnakesProto.GameMessage createJoinMessage(
            long msgSeq,
            String playerName,
            String gameName,
            SnakesProto.NodeRole requestedRole) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setJoin(SnakesProto.GameMessage.JoinMsg.newBuilder()
                        .setPlayerName(playerName)
                        .setGameName(gameName)
                        .setRequestedRole(requestedRole)
                        .build())
                .build();
    }

    public static SnakesProto.GameMessage createAckMessage(
            long msgSeq, int senderId, int receiverId) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setSenderId(senderId) // Для мастера может быть 0 или его playerId
                .setReceiverId(receiverId) // ID, который назначается клиенту
                .setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build())
                .build();
    }

    public static SnakesProto.GameMessage createPingMessage(long msgSeq, int senderId) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setSenderId(senderId)
                .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                .build();
    }

    public static SnakesProto.GameMessage createStateMessage(
            long msgSeq, int senderId, SnakesProto.GameState state) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setSenderId(senderId)
                .setState(SnakesProto.GameMessage.StateMsg.newBuilder()
                        .setState(state)
                        .build())
                .build();
    }

    public static SnakesProto.GameMessage createAnnouncementMessage(
            long msgSeq, SnakesProto.GameAnnouncement announcement) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setAnnouncement(SnakesProto.GameMessage.AnnouncementMsg.newBuilder()
                        .addGames(announcement)
                        .build())
                .build();
    }

    public static SnakesProto.GameMessage createDiscoverMessage(long msgSeq) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setDiscover(SnakesProto.GameMessage.DiscoverMsg.newBuilder().build())
                .build();
    }

    public static SnakesProto.GameMessage createErrorMessage(
            long msgSeq, int senderId, String errorMessage) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setSenderId(senderId)
                .setError(SnakesProto.GameMessage.ErrorMsg.newBuilder()
                        .setErrorMessage(errorMessage)
                        .build())
                .build();
    }

    public static SnakesProto.GameMessage createRoleChangeMessage (
            long msgSeq,
            int senderId,
            int receiverId,
            SnakesProto.NodeRole senderRole,
            SnakesProto.NodeRole receiverRole) {
        return MessageUtils.createBaseMessage(msgSeq)
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                        .setSenderRole(senderRole)
                        .setReceiverRole(receiverRole)
                        .build())
                .build();
    }
}
