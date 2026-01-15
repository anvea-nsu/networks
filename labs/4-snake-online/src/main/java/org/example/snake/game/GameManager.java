package org.example.snake.game;

import org.example.snake.DiscoveredGame;
import org.example.snake.config.GameConfig;
import org.example.snake.messages.NetworkManager;
import org.example.snake.protocol.SnakesProto;
import org.example.snake.roles.NodeRole;
import org.example.snake.roles.OnRoleChangeListener;
import org.example.snake.roles.Role;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameManager implements OnRoleChangeListener {
    private Role player;
    private IGameListener listener;

    private static class GameKey {
        final String gameName;
        final String hostAddress;
        final int port;

        GameKey(String gameName, String hostAddress, int port) {
            this.gameName = gameName;
            this.hostAddress = hostAddress;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GameKey)) return false;
            GameKey gameKey = (GameKey) o;
            return port == gameKey.port &&
                    Objects.equals(gameName, gameKey.gameName) &&
                    Objects.equals(hostAddress, gameKey.hostAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gameName, hostAddress, port);
        }
    }

    private static class TimestampedGame {
        final DiscoveredGame game;
        final long timestamp;

        TimestampedGame(DiscoveredGame game, long timestamp) {
            this.game = game;
            this.timestamp = timestamp;
        }
    }

    private final Map<GameKey, TimestampedGame> discoveredGames = new ConcurrentHashMap<>();

    public GameManager(String playerName) throws IOException {
        var networkManager = new NetworkManager();
        networkManager.setGameManager(this);
        player = Role.createBeginingRole(networkManager, playerName, this);
        networkManager.start();
        player.start();
    }

    public void leaveGame() {
        player.leaveGame();
        player = player.changeRole(NodeRole.MENU);
        discoveredGames.clear();
        listener.leaveGame();
    }

    public void stop() {
        player.leaveGame();
        player.stop();
        player.stopNetworkManager();
    }

    public void createNewGame(GameConfig config) {
        player.setConfig(config);
        player = player.changeRole(NodeRole.MASTER);
    }

    public void joinGame(DiscoveredGame game, SnakesProto.NodeRole role) {
        player.joinGame(game, role);
    }

    public void handleIncomingMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {
//        System.out.println(message.getTypeCase() + " from " + sender);
        if (!message.hasDiscover() && !message.hasJoin() && !message.hasAnnouncement()) {
//            System.out.println("update");
            player.updateNodeActivity(sender);
        }

        switch (message.getTypeCase()) {
            case STATE:
                player.handleStateMessage(message, sender);
                break;

            case STEER:
                player.handleSteerMessage(message, sender);
                break;

            case JOIN:
                player.handleJoinRequest(message.getJoin(), sender, message.getMsgSeq());
                break;

            case ANNOUNCEMENT:
                updateDiscoveredGames(message.getAnnouncement(), sender);
                break;

            case ACK:
                handleAckMessage(message, sender);
                break;

            case ROLE_CHANGE:
                player.handleRoleChange(message, sender);
                break;

            case ERROR:
                player.handleErrorMessage(message);
                break;

            case DISCOVER:
                player.handleDiscover(sender);
                break;

            case PING:
                player.handlePing(message, sender);
                break;
        }
    }

    private void handleAckMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {
        player.handleAckMessage(message);
    }

    public void sendSteerCommand(SnakesProto.Direction direction) {
        player.sendSteerMessage(direction);
    }

    private void updateDiscoveredGames(SnakesProto.GameMessage.AnnouncementMsg announcement, InetSocketAddress sender) {
        if (sender.equals(player.getMyAddress())) return;

        for (SnakesProto.GameAnnouncement game : announcement.getGamesList()) {
            GameKey key = new GameKey(
                    game.getGameName(),
                    sender.getAddress().getHostAddress(),
                    sender.getPort()
            );
            DiscoveredGame discoveredGame = new DiscoveredGame(
                    game.getGameName(),
                    sender,
                    game.getPlayers().getPlayersCount(),
                    GameConfig.fromProtobuf(game.getConfig())
            );
            discoveredGames.put(key, new TimestampedGame(discoveredGame, System.currentTimeMillis()));
        }
    }

    public boolean isGameRunning() {
        return player.getRole() != NodeRole.MENU;
    }

    public SnakesProto.GameState getGameState() {
        return player.getState() != null ? player.getState().toProtobuf() : null;
    }

    public List<DiscoveredGame> getDiscoveredGames() {
        long now = System.currentTimeMillis();
        discoveredGames.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 10_000); // 10 секунд

        return discoveredGames.values().stream()
                .map(ts -> ts.game)
                .collect(Collectors.toList());
    }

    public int getPlayerId() {
        return player.getPlayerId();
    }

    public String getPlayerName() {
        return player.getPlayerName();
    }

    public GameConfig getConfig() {
        return player.getConfig();
    }

    public SnakesProto.NodeRole getCurrentRole() {
        return player.getRole().toProtobuf();
    }

    public void setListener(IGameListener listener) {
        this.listener = listener;
    }

    @Override
    public void onRoleChangeRequest(NodeRole newRole) {
        if (newRole == player.getRole()) return;
        player = player.changeRole(newRole);
        player.start();
    }
}