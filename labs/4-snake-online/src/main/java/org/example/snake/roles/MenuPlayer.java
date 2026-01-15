package org.example.snake.roles;

import org.example.snake.DiscoveredGame;
import org.example.snake.config.GameConfig;
import org.example.snake.game.GameState;
import org.example.snake.messages.NetworkManager;
import org.example.snake.protocol.SnakesProto;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MenuPlayer extends Role{
    private NodeRole newRole;
    private long joinRequestTime = 0;
    private static final long JOIN_TIMEOUT = 3000; // 3 секунды на подключение

    public MenuPlayer(NetworkManager networkManager, String playerName, OnRoleChangeListener listener) {
        super(null, networkManager, 0, playerName, listener, null);
        role = NodeRole.MENU;
    }

    @Override
    protected void checkNodeActivity(List<InetSocketAddress> adds) {

    }

    @Override
    public void handleStateMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {

    }

    @Override
    public GameState getState() {
        return null;
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

    }

    @Override
    public void handleSteerMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {

    }

    @Override
    public void handleRoleChange(InetSocketAddress sender, SnakesProto.NodeRole role) {

    }

    @Override
    public void joinGame(DiscoveredGame game, SnakesProto.NodeRole role) {
        masterAddress = game.getMasterAddress();
        newRole = NodeRole.fromProtobuf(role);
        config = game.getConfig();
        joinRequestTime = System.currentTimeMillis();

        System.out.println("Присоединяемся к игре: " + game.getGameName() + " как " + role + " к " + masterAddress);
        networkManager.sendJoinMessage(playerName, game.getGameName(), role, masterAddress);
    }

    @Override
    public void start() {
        scheduler.scheduleAtFixedRate(networkManager::sendDiscoverMessage, 0, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::resendMessages, 100, 100, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::checkJoinTimeout, 200, 200, TimeUnit.MILLISECONDS);
    }

    private void checkJoinTimeout() {
        if (joinRequestTime > 0 && System.currentTimeMillis() - joinRequestTime > JOIN_TIMEOUT) {
            System.err.println("Таймаут подключения к игре");
            joinRequestTime = 0;
            // Можно добавить уведомление пользователя о проблеме
        }
    }

    @Override
    public void handleAckMessage(SnakesProto.GameMessage message) {
        super.handleAckMessage(message);
        if (joinRequestTime > 0) {
            playerId = message.getReceiverId();
            joinRequestTime = 0;
            listener.onRoleChangeRequest(newRole);
        }
    }

    @Override
    public void handleErrorMessage(SnakesProto.GameMessage message) {
        super.handleErrorMessage(message);
        joinRequestTime = 0;
        System.err.println("Ошибка при подключении: " + message.getError().getErrorMessage());
    }
}