package org.example.snake.roles;

import org.example.snake.config.GameConfig;
import org.example.snake.messages.NetworkManager;
import org.example.snake.protocol.SnakesProto;

import java.net.InetSocketAddress;

public class Viewer extends Normal {
    public Viewer(GameConfig config, NetworkManager networkManager, int i, String playerName, OnRoleChangeListener listener, InetSocketAddress masterAddress) {
        super(config, networkManager, i, playerName, listener, masterAddress);
        role = NodeRole.VIEWER;
    }

    @Override
    public void sendSteerMessage(SnakesProto.Direction direction) {
    }
}
