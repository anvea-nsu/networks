package org.example.snake.entities;

import org.example.snake.protocol.SnakesProto;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class Player {
    private final int id;
    private final String name;
//    private InetAddress ipAddress;
//    private int port;
    private InetSocketAddress address;
    private SnakesProto.NodeRole role;
    private SnakesProto.PlayerType type;
    private int score;

    public Player(int id, String name, InetSocketAddress address,
                  SnakesProto.NodeRole role, SnakesProto.PlayerType type) {
        this.id = id;
        this.name = name;
//        this.ipAddress = ipAddress;
//        this.port = port;
        this.role = role;
        this.type = type;
        this.score = 0;
        this.address = address;
    }

    public static Player fromProtobuf(SnakesProto.GamePlayer protoPlayer) {
        try {
            InetAddress address = null;
            if (protoPlayer.hasIpAddress()) {
                address = InetAddress.getByName(protoPlayer.getIpAddress());
            }

            var player = new Player(
                    protoPlayer.getId(),
                    protoPlayer.getName(),
                    new InetSocketAddress(address, protoPlayer.hasPort() ? protoPlayer.getPort() : 0),
                    protoPlayer.getRole(),
                    protoPlayer.getType()
            );
            player.setScore(protoPlayer.getScore());
            return player;
        } catch (UnknownHostException e) {
            throw new RuntimeException("Invalid IP address in player", e);
        }
    }

    public SnakesProto.GamePlayer toProtobuf() {
        SnakesProto.GamePlayer.Builder builder = SnakesProto.GamePlayer.newBuilder()
                .setId(id)
                .setName(name)
                .setRole(role)
                .setType(type)
                .setScore(score);

        if (address.getAddress() != null) {
            builder.setIpAddress(address.getAddress().getHostAddress());
        }
        if (address.getPort() != 0) {
            builder.setPort(address.getPort());
        }

        return builder.build();
    }

    public int getId() { return id; }
    public String getName() { return name; }

    public InetSocketAddress getAddress() {
        return address;
    }

    //    public InetAddress getIpAddress() { return ipAddress; }
//    public int getPort() { return port; }
    public SnakesProto.NodeRole getRole() { return role; }
    public void setRole(SnakesProto.NodeRole role) {
        this.role = role;
    }
    public SnakesProto.PlayerType getType() { return type; }
    public void setType(SnakesProto.PlayerType type) { this.type = type; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }
    public void incrementScore() { score++; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return id == player.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}