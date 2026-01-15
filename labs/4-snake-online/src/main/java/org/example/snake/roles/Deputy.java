package org.example.snake.roles;

import org.example.snake.config.GameConfig;
import org.example.snake.game.GameState;
import org.example.snake.messages.NetworkManager;
import org.example.snake.protocol.SnakesProto;

import java.net.InetSocketAddress;
import java.util.List;

public class Deputy extends Normal {
    public Deputy(GameConfig config, NetworkManager networkManager, int i, String playerName, OnRoleChangeListener listener, InetSocketAddress masterAddress) {
        super(config, networkManager, i, playerName, listener, masterAddress);
        role = NodeRole.DEPUTY;
    }

    public void updateFromProtoState(SnakesProto.GameState protoState) {
        if (protoState.getStateOrder() > lastStateOrder) {
            lastStateOrder = protoState.getStateOrder();
            state = GameState.fromProtobuf(protoState);

            if (myAddress == null) {
                myAddress = findMyAddress();
                System.out.println("My deputy address " + myAddress);
            }
        }
    }

    @Override
    protected void checkNodeActivity(List<InetSocketAddress> adds) {
//        if (!adds.contains(masterAddress)) return;
        listener.onRoleChangeRequest(NodeRole.MASTER);
    }
}
