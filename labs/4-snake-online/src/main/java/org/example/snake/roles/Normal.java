package org.example.snake.roles;

import org.example.snake.DiscoveredGame;
import org.example.snake.config.GameConfig;
import org.example.snake.game.GameState;
import org.example.snake.messages.NetworkManager;
import org.example.snake.protocol.SnakesProto;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Normal extends Role {
    protected GameState state;

    public Normal(GameConfig config, NetworkManager networkManager, int playerId, String playerName, OnRoleChangeListener listener, InetSocketAddress master) {
        super(config, networkManager, playerId, playerName, listener, master);
        role = NodeRole.NORMAL;
    }

    private void sendPing() {
        if (masterAddress == null) return;
        networkManager.sendPing(masterAddress, playerId);
    }

    protected void checkNodeActivity(List<InetSocketAddress> adds) {
        if (!adds.contains(masterAddress)) return;

        if (deputyAddress != null) {
            masterAddress = deputyAddress;
            System.out.println("Переключились на депутата: " + deputyAddress);
        } else {
            System.out.println("Мастер и депутат недоступны, выходим из игры");
            listener.leaveGame();
        }
    }

    @Override
    public void handleStateMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {
        System.out.println("State received from " + sender);
        updateFromProtoState(message.getState().getState());
        networkManager.sendAck(message.getMsgSeq(), playerId, message.getSenderId(), sender);
    }

    public void updateFromProtoState(SnakesProto.GameState protoState) {
        if (protoState.getStateOrder() > lastStateOrder) {
            lastStateOrder = protoState.getStateOrder();
            state = GameState.fromProtobuf(protoState);

            updateDeputyFromState(protoState);
            updateMasterFromState(protoState);

            if (myAddress == null) {
                myAddress = findMyAddress();
                System.out.println("My normal address " + myAddress);
            }
        }
    }

    protected InetSocketAddress findMyAddress() {
        for (var player: state.getPlayers()) {
            if (player.getId() == playerId) {
                return player.getAddress();
            }
        }
        return null;
    }

    private void updateMasterFromState(SnakesProto.GameState state) {
        for (SnakesProto.GamePlayer player : state.getPlayers().getPlayersList()) {
            if (player.getRole() == SnakesProto.NodeRole.MASTER) {
                if (player.getPort() == 0) break;
                masterAddress = new InetSocketAddress(player.getIpAddress(), player.getPort());
                break;
            }
        }
    }

    public GameState getState() {
        return state;
    }

    @Override
    public void handleJoinRequest(SnakesProto.GameMessage.JoinMsg joinMsg, InetSocketAddress sender, long msgSeq) {
    }

    @Override
    public void handlePing(SnakesProto.GameMessage message, InetSocketAddress sender) {
    }

    @Override
    public void handleDiscover(InetSocketAddress sender) {
    }

    @Override
    public void sendSteerMessage(SnakesProto.Direction direction) {
        networkManager.sendSteerMessage(direction, masterAddress, playerId);
    }

    @Override
    public void handleSteerMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {
    }

    @Override
    public void handleRoleChange(InetSocketAddress sender, SnakesProto.NodeRole role) {
        if (sender.equals(masterAddress) && role != SnakesProto.NodeRole.MASTER) {
            masterAddress = deputyAddress;
        }
        if (role == SnakesProto.NodeRole.MASTER) {
            masterAddress = sender;
        }
    }

    @Override
    public void joinGame(DiscoveredGame game, SnakesProto.NodeRole role) {

    }

    @Override
    public void start() {
        System.out.println("Normal role started for player " + playerId);
        scheduler.scheduleAtFixedRate(this::checkNodeActivity, 0, config.getStateDelayMs() / 5, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::sendPing, 0, config.getStateDelayMs() / 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::resendMessages, config.getStateDelayMs() / 10, config.getStateDelayMs() / 10, TimeUnit.MILLISECONDS);
    }
}