package org.example.snake;

import org.example.snake.config.GameConfig;

import java.net.InetSocketAddress;

public class DiscoveredGame {
    private final String gameName;
    private final String description;
    private final InetSocketAddress masterAddress;
    private final GameConfig config;
    private final int playerCount;

    public DiscoveredGame(String gameName, String description, InetSocketAddress masterAddress, GameConfig config, int playerCount) {
        this.gameName = gameName;
        this.description = description;
        this.masterAddress = masterAddress;
        this.config = config;
        this.playerCount = playerCount;
    }

    public DiscoveredGame(String gameName, InetSocketAddress masterAddress, int playerCount, GameConfig config) {
        this(gameName, null, masterAddress, config, playerCount);
    }

    // Геттеры
    public String getGameName() { return gameName; }
    public String getDescription() { return description; }
    public InetSocketAddress getMasterAddress() { return masterAddress; }
    public int getPlayerCount() { return playerCount; }
    public GameConfig getConfig() { return config; }

    @Override
    public String toString() {
        if (masterAddress == null) {
            return gameName + (description != null ? " - " + description : "");
        }
        return String.format("%s (%d игроков) - %s", gameName, playerCount,
                masterAddress.getAddress().getHostAddress());
    }
}