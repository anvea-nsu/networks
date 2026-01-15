package org.example.snake.roles;

import org.example.snake.DiscoveredGame;
import org.example.snake.config.GameConfig;
import org.example.snake.entities.Player;
import org.example.snake.game.GameEngine;
import org.example.snake.game.GameState;
import org.example.snake.messages.NetworkManager;
import org.example.snake.protocol.SnakesProto;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Master extends Role {
    private final GameEngine gameEngine;

    private final Map<InetSocketAddress, Integer> playersAddress = new ConcurrentHashMap<>();
    private final Map<Integer, SnakesProto.Direction> pendingDirections = new ConcurrentHashMap<>();
    private int maxId = -1;

    public Master(GameConfig config, NetworkManager networkManager, int playerId, String playerName,
                  OnRoleChangeListener listener, InetSocketAddress masterAddress, GameState state){
        super(config, networkManager, playerId, playerName, listener, masterAddress);
        role = NodeRole.MASTER;
        gameEngine = new GameEngine(config);
        gameEngine.setState(state);
    }

    public Master(GameConfig config, NetworkManager networkManager, String playerName, OnRoleChangeListener listener){
        super(config, networkManager, 0, playerName, listener, null);
        playerId = generateNewPlayerId();
        role = NodeRole.MASTER;
        gameEngine = new GameEngine(config);

        Player player = new Player(playerId, playerName, new InetSocketAddress("", 0), role.toProtobuf(), SnakesProto.PlayerType.HUMAN);
        boolean success = gameEngine.addPlayer(player);
        masterAddress = networkManager.getMyAddress();

        if (success) {
            networkManager.addPlayerAddress(playerId, masterAddress);
            startScheduledTasks();
            System.out.println("Новая игра создана. Вы мастер. ");
        } else {
            scheduler.shutdown();
            System.err.println("Не удалось создать игру: нет места для спавна");
        }
    }

    private void updatePlayers() {
        var masterId = 0;
        for (var player: gameEngine.getState().getPlayers()){
            var address = player.getAddress();
            if (address.equals(myAddress)){
                player.setRole(SnakesProto.NodeRole.MASTER);
                continue;
            }

            if (address.equals(masterAddress)){
                masterId = player.getId();
                continue;
            }

            if (address.equals(new InetSocketAddress("", 0))) {
                masterId = player.getId();
                continue;
            }
            networkManager.addPlayerAddress(player.getId(), address);
            playersAddress.put(address, player.getId());
            networkManager.sendRoleChangeMessage(player.getId(), address, SnakesProto.NodeRole.MASTER, player.getRole());
        }
        gameEngine.removePlayer(masterId);
    }

    private void startScheduledTasks() {
        scheduler.scheduleAtFixedRate(this::checkNodeActivity, 0, config.getStateDelayMs() / 5, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::updateGame, 0, config.getStateDelayMs(), TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::resendMessages, config.getStateDelayMs() / 10, config.getStateDelayMs() / 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(() ->
                        networkManager.sendGameAnnouncement(createGameAnnouncement()),
                0, 1, TimeUnit.SECONDS);
    }

    private void updateGame() {
        try {
            var deads = gameEngine.update(new HashMap<>(pendingDirections));
            pendingDirections.clear();

            var deputyIsDead = false;
            var masterIsDead = false;
            for (var player: deads) {
                if (player.getId() == playerId)
                    masterIsDead = true;
                else if (player.getAddress().equals(deputyAddress))
                {
                    deputyIsDead = true;
                } else {
                    networkManager.sendRoleChangeMessage(player.getId(), player.getAddress(), role.toProtobuf(), SnakesProto.NodeRole.VIEWER);
                }
            }
            if (deputyIsDead)
                selectNewDeputy();

            updateDeputyFromState(gameEngine.getState().toProtobuf());
            if (deputyAddress == null) {
                selectNewDeputy();
            }

            SnakesProto.GameState state = gameEngine.getState().toProtobuf();

            if (state.getStateOrder() != lastStateOrder) {
                lastStateOrder = state.getStateOrder();
                var map = new HashMap<Integer, InetSocketAddress>();
                for (var player: gameEngine.getState().getPlayers()) {
                    if (player.getRole() != SnakesProto.NodeRole.MASTER) {
                        map.put(player.getId(), player.getAddress());
                    }
                }
                networkManager.broadcastState(state, playerId, map);
            }

            if (masterIsDead) {
                if (deputyAddress == null) {
                    listener.leaveGame();
                } else {
                    masterAddress = deputyAddress;
                    networkManager.sendRoleChangeMessage(playersAddress.get(deputyAddress), deputyAddress, SnakesProto.NodeRole.VIEWER, SnakesProto.NodeRole.MASTER);
                    listener.onRoleChangeRequest(NodeRole.VIEWER);
                }
            }

        } catch (Exception e) {
            System.err.println("Ошибка при обновлении игры: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handleJoinRequest(SnakesProto.GameMessage.JoinMsg joinMsg, InetSocketAddress sender, long msgSeq) {
        System.out.println("!!! MASTER: Получен JOIN запрос от " + sender + ", имя: " + joinMsg.getPlayerName());

        // Ищем игрока по имени (а не по адресу, т.к. порт может измениться)
        Player existingPlayer = findPlayerByName(joinMsg.getPlayerName());

        if (existingPlayer != null) {
            System.out.println("Найден существующий игрок: " + existingPlayer.getName() + " (ID=" + existingPlayer.getId() + ")");
            System.out.println("Старый адрес: " + existingPlayer.getAddress() + ", новый адрес: " + sender);

            // Удаляем старый адрес из маппинга
            InetSocketAddress oldAddress = existingPlayer.getAddress();
            playersAddress.remove(oldAddress);
            lastTimeActive.remove(oldAddress);

            // Проверяем, был ли это депутат
            if (existingPlayer.getRole() == SnakesProto.NodeRole.DEPUTY) {
                System.out.println("Переподключение депутата");
                deputyAddress = null; // Временно сбрасываем, обновим после
            }

            // Удаляем старого игрока
            gameEngine.removePlayer(existingPlayer.getId());
            networkManager.removePlayerAddress(existingPlayer.getId());
        }

        if (!gameEngine.canAcceptNewPlayers()) {
            System.err.println("Нет места для новых игроков");
            networkManager.sendError("Нет места для новых игроков", sender);
            return;
        }

        int newPlayerId = generateNewPlayerId();
        Player newPlayer = new Player(newPlayerId, joinMsg.getPlayerName(), sender,
                joinMsg.getRequestedRole(), SnakesProto.PlayerType.HUMAN);

        boolean success = gameEngine.addPlayer(newPlayer);
        if (success) {
            System.out.println("Отправляем ACK с receiverId=" + newPlayerId + " на адрес " + sender);
            networkManager.sendAck(msgSeq, playerId, newPlayerId, sender);
            networkManager.addPlayerAddress(newPlayerId, sender);
            playersAddress.put(sender, newPlayerId);
            updateNodeActivity(sender);

            System.out.println("!!! Игрок " + joinMsg.getPlayerName() + " успешно присоединился с ID: " + newPlayerId);

            if (deputyAddress == null && newPlayer.getRole() == SnakesProto.NodeRole.NORMAL) {
                appointDeputy(newPlayerId, sender);
            }
        } else {
            networkManager.sendError("Не удалось добавить игрока", sender);
            System.err.println("Не удалось добавить игрока " + joinMsg.getPlayerName());
        }
    }

    private Player findPlayerByName(String playerName) {
        for (Player player : gameEngine.getState().getPlayers()) {
            if (player.getName().equals(playerName)) {
                return player;
            }
        }
        return null;
    }

    @Override
    public void handlePing(SnakesProto.GameMessage message, InetSocketAddress sender) {
        updateNodeActivity(sender);
        networkManager.sendAck(message.getMsgSeq(), playerId, message.getSenderId(), sender);
    }

    @Override
    public void handleDiscover(InetSocketAddress sender) {
        networkManager.sendGameAnnouncement(createGameAnnouncement(), sender);
    }

    @Override
    public void sendSteerMessage(SnakesProto.Direction direction) {
        pendingDirections.put(playerId, direction);
    }

    @Override
    public void handleSteerMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {
        Integer playerIdFromAddress = playersAddress.get(sender);
        if (playerIdFromAddress != null) {
            pendingDirections.put(playerIdFromAddress, message.getSteer().getDirection());
            networkManager.sendAck(message.getMsgSeq(), playerId, message.getSenderId(), sender);
        } else {
            System.err.println("Получено STEER сообщение от неизвестного адреса: " + sender);
        }
    }

    @Override
    public void handleRoleChange(InetSocketAddress sender, SnakesProto.NodeRole role) {
        var player = findPlayer(sender);
        if (player != null){
            if (player.getRole() == SnakesProto.NodeRole.DEPUTY) {
                selectNewDeputy();
            }
            player.setRole(role);
            var snake = gameEngine.getState().getSnakeByPlayerId(player.getId());
            if (snake != null) {
                snake.setState(SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
            }
        }
    }

    @Override
    public void joinGame(DiscoveredGame game, SnakesProto.NodeRole role) {

    }

    @Override
    public void start() {
        updatePlayers();
        startScheduledTasks();
        updateId();
    }

    private void updateId() {
        for (var player: gameEngine.getState().getPlayers()) {
            if (player.getId() > maxId)
                maxId = player.getId();
        }
    }

    @Override
    public void updateNodeActivity(InetSocketAddress address) {
        if (myAddress == null && findPlayer(address) == null) {
            myAddress = address;
            gameEngine.getState().getPlayer(playerId).setAddress(address);
            System.out.println("My master address " + myAddress);
        }
        super.updateNodeActivity(address);
    }

    private Player findPlayer(InetSocketAddress address) {
        for (var player: gameEngine.getState().getPlayers()) {
            if (player.getAddress().equals(address)) {
                return player;
            }
        }

        return null;
    }

    protected void checkNodeActivity(List<InetSocketAddress> adds) {
        boolean deputyDisconnected = false;
        for (var address: adds){
            Integer playerId = playersAddress.get(address);
            if (playerId != null) {
                System.out.println("Игрок " + playerId + " отключился по таймауту");

                Player player = gameEngine.getState().getPlayer(playerId);
                if (player != null && player.getRole() == SnakesProto.NodeRole.DEPUTY) {
                    deputyDisconnected = true;
                }

                var snake = gameEngine.getState().getSnakeByPlayerId(playerId);
                if (snake != null) {
                    snake.setState(SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
                }
                gameEngine.removePlayer(playerId);
                playersAddress.remove(address);
                networkManager.removePlayerAddress(playerId);
            }
        }

        if (deputyDisconnected) {
            selectNewDeputy();
        }
    }

    @Override
    public void handleStateMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {
    }

    private void appointDeputy(int playerId, InetSocketAddress address) {
        Player player = gameEngine.getState().getPlayer(playerId);
        if (player != null) {
            player.setRole(SnakesProto.NodeRole.DEPUTY);
            deputyAddress = address;
            System.out.println("Назначен новый депутат: " + player.getName());

            networkManager.sendRoleChangeMessage(playerId, address,
                    SnakesProto.NodeRole.MASTER, SnakesProto.NodeRole.DEPUTY);
        }
    }

    private void selectNewDeputy() {
        for (Player player : gameEngine.getState().getPlayers()) {
            if (player.getRole() == SnakesProto.NodeRole.NORMAL && player.getId() != playerId) {
                var address = player.getAddress();
                if (address != null) {
                    appointDeputy(player.getId(), address);
                    break;
                }
            }
        }
    }

    private SnakesProto.GameAnnouncement createGameAnnouncement() {
        return SnakesProto.GameAnnouncement.newBuilder()
                .setGameName("Игра-" + playerName)
                .setConfig(config.toProtobuf())
                .setPlayers(gameEngine.getState().getPlayersProto())
                .setCanJoin(gameEngine.canAcceptNewPlayers())
                .build();
    }

    private int generateNewPlayerId() {
        return ++maxId;
    }

    public GameState getState() {
        return gameEngine.getState();
    }
}