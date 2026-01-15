package org.example.snake.roles;

import org.example.snake.DiscoveredGame;
import org.example.snake.config.GameConfig;
import org.example.snake.game.GameState;
import org.example.snake.messages.NetworkManager;
import org.example.snake.protocol.SnakesProto;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class Role {
    protected int playerId;
    protected String playerName;
    protected NodeRole role;

    protected GameConfig config;
    protected final int timeout;
    protected final NetworkManager networkManager;
    protected final OnRoleChangeListener listener;

    protected final Map<InetSocketAddress, Long> lastTimeActive = new ConcurrentHashMap<>();

    protected InetSocketAddress masterAddress;
    protected InetSocketAddress deputyAddress = null;

    protected InetSocketAddress myAddress = null;

    protected final ScheduledExecutorService scheduler;

    protected int lastStateOrder = -1;

    public Role(GameConfig config, NetworkManager networkManager, int playerId, String playerName, OnRoleChangeListener listener, InetSocketAddress masterAddress){
        this.config = config;
        this.networkManager = networkManager;
        this.playerId = playerId;
        this.playerName = playerName;
        this.listener = listener;
        this.masterAddress = masterAddress;

        timeout = config != null ? config.getStateDelayMs() * 4 / 5 : 0;
        scheduler = Executors.newScheduledThreadPool(4);

//        System.out.println(masterAddress);
    }

    protected void checkNodeActivity() {
        long currentTime = System.currentTimeMillis();
        List<InetSocketAddress> adds = new ArrayList<>();
        for (var entry: lastTimeActive.entrySet()){
            if (currentTime - entry.getValue() >= timeout){
                adds.add(entry.getKey());
            }
        }

//        System.out.println(lastTimeActive);
        for (var add: adds){
            lastTimeActive.remove(add);
        }

        if (!adds.isEmpty())
            checkNodeActivity(adds);
    }

    protected void updateDeputyFromState(SnakesProto.GameState state) {
        deputyAddress = null;
        for (SnakesProto.GamePlayer player : state.getPlayers().getPlayersList()) {
            if (player.getRole() == SnakesProto.NodeRole.DEPUTY) {
                deputyAddress = new InetSocketAddress(player.getIpAddress(), player.getPort());
                break;
            }
        }
    }

    public void leaveGame() {
        if (masterAddress != null) {
            networkManager.sendRoleChangeMessage(0, masterAddress, SnakesProto.NodeRole.VIEWER, SnakesProto.NodeRole.MASTER);
        } else if (deputyAddress != null) {
            networkManager.sendRoleChangeMessage(0, deputyAddress, SnakesProto.NodeRole.VIEWER, SnakesProto.NodeRole.MASTER);
        }
    }

    protected abstract void checkNodeActivity(List<InetSocketAddress> adds);

    public abstract void handleStateMessage(SnakesProto.GameMessage message, InetSocketAddress sender);
    public abstract GameState getState();
    public abstract void handleJoinRequest(SnakesProto.GameMessage.JoinMsg joinMsg, InetSocketAddress sender, long msgSeq);
    public abstract void handlePing(SnakesProto.GameMessage message, InetSocketAddress sender);
    public abstract void handleDiscover(InetSocketAddress sender);
    public abstract void sendSteerMessage(SnakesProto.Direction direction);
    public abstract void handleSteerMessage(SnakesProto.GameMessage message, InetSocketAddress sender);
    public abstract void handleRoleChange(InetSocketAddress sender, SnakesProto.NodeRole role);
    public abstract void joinGame(DiscoveredGame game, SnakesProto.NodeRole role);

    public NodeRole getRole() {
        return role;
    }

    public GameConfig getConfig() {
        return config;
    }

    public void stop() {
        scheduler.shutdown();
    }

    public abstract void start();

    public Role changeRole(NodeRole newRole) {
        stop();

        var player = getBuilder()
                .setRole(newRole)
                .setConfig(config)
                .setNetworkManager(networkManager)
                .setPlayerId(playerId)
                .setPlayerName(playerName)
                .setMasterAddress(masterAddress)
                .setState(getState())
                .setListener(listener)
                .build();
        player.setDeputyAddress(deputyAddress);
        player.setMyAddress(myAddress);
        return player;
    }



    public void updateNodeActivity(InetSocketAddress address) {
        if (address.equals(myAddress)) return;
        lastTimeActive.put(address, System.currentTimeMillis());
    }

    public void setDeputyAddress(InetSocketAddress newDeputyAddress) {
        deputyAddress = newDeputyAddress;
    }

    public void setMyAddress(InetSocketAddress myAddress) {
        this.myAddress = myAddress;
    }

    public void handleAckMessage(SnakesProto.GameMessage message) {
        removeMessage(message.getMsgSeq());
    }

    public void handleErrorMessage(SnakesProto.GameMessage message) {
        removeMessage(message.getMsgSeq());
        System.out.println("Ошибка от сервера: " + message.getError().getErrorMessage());
    }

    private void removeMessage(long msgSeq) {
        networkManager.removeMessage(msgSeq);
    }

    protected void resendMessages() {
        var messages = networkManager.getPendingMessages();
        var time = System.currentTimeMillis();
        for (var message: messages.entrySet()) {
            if (time - message.getValue().getSendTime() >= config.getStateDelayMs() / 10) {
                if (message.getValue().getRetryCount() < 3){
                    networkManager.resendMessage(message.getKey());
                } else {
                    networkManager.removeMessage(message.getKey());
                }
            }
        }
    }

    public static Builder getBuilder() {
        return new Builder();
    }

    public int getPlayerId() {
        return playerId;
    }

//    public void setRole(NodeRole role) {
//        this.role = role;
//    }

    public void setConfig(GameConfig config) {
        this.config = config;
    }

    public void stopNetworkManager() {
        networkManager.stop();
    }

    public static Role createBeginingRole(NetworkManager networkManager, String playerName, OnRoleChangeListener listener) {
        return new MenuPlayer(networkManager, playerName, listener);
    }

    public void handleRoleChange(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (message.getRoleChange().hasReceiverRole()) {
            var role = message.getRoleChange().getReceiverRole();
            listener.onRoleChangeRequest(NodeRole.fromProtobuf(role));
        }

        if (message.getRoleChange().hasSenderRole()){
            handleRoleChange(sender, message.getRoleChange().getSenderRole());
        }
        networkManager.sendAck(message.getMsgSeq(), playerId, message.getSenderId(), sender);
    }

    public InetSocketAddress getMyAddress() {
        return myAddress;
    }

    public String getPlayerName() {
        return playerName;
    }

    public static class Builder {
        private int playerId;
        private String playerName;
        private NodeRole role;

        private GameConfig config;
        private NetworkManager networkManager;
        private OnRoleChangeListener listener;
        private InetSocketAddress masterAddress;
        private GameState state;

        public Role build() {
//            if (role == null) {
//                return new MenuPlayer(networkManager, playerName, listener);
//            }
            return switch (role){
                case NORMAL -> new Normal(config, networkManager, playerId, playerName, listener, masterAddress);
                case DEPUTY -> new Deputy(config, networkManager, playerId, playerName, listener, masterAddress);
                case MASTER -> state != null ? new Master(config, networkManager, playerId, playerName, listener, masterAddress, state) :
                        new Master(config, networkManager, playerName, listener);
                case VIEWER -> new Viewer(config, networkManager, playerId, playerName, listener, masterAddress);
                case MENU -> new MenuPlayer(networkManager, playerName, listener);
            };
        }

        public Builder setPlayerId(int playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder setPlayerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder setRole(NodeRole role) {
            this.role = role;
            return this;
        }

        public Builder setConfig(GameConfig config) {
            this.config = config;
            return this;
        }

        public Builder setNetworkManager(NetworkManager networkManager) {
            this.networkManager = networkManager;
            return this;
        }

        public Builder setMasterAddress(InetSocketAddress masterAddress) {
            this.masterAddress = masterAddress;
            return this;
        }

        public Builder setState(GameState state) {
            this.state = state;
            return this;
        }

        public Builder setListener(OnRoleChangeListener listener) {
            this.listener = listener;
            return this;
        }
    }
}
