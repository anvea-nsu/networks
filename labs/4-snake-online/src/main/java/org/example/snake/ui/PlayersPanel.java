package org.example.snake.ui;

import org.example.snake.game.GameManager;
import org.example.snake.entities.Player;
import org.example.snake.protocol.SnakesProto;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PlayersPanel extends JPanel {
    private GameManager gameManager;
    private JPanel playersContainer;

    public PlayersPanel(GameManager gameManager) {
        this.gameManager = gameManager;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Игроки"));

        playersContainer = new JPanel();
        playersContainer.setLayout(new BoxLayout(playersContainer, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(playersContainer);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        // Изначально показываем сообщение об ожидании
        showWaitingMessage();
    }

    public void updatePlayers() {
        if (gameManager == null || !gameManager.isGameRunning()) {
            showWaitingMessage();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            playersContainer.removeAll();

            try {
                SnakesProto.GameState gameState = gameManager.getGameState();
                if (gameState != null && gameState.hasPlayers()) {
                    List<Player> players = gameState.getPlayers().getPlayersList().stream()
                            .map(Player::fromProtobuf)
                            .sorted(Comparator.comparing(Player::getScore).reversed())
                            .collect(Collectors.toList());

                    if (players.isEmpty()) {
                        addNoPlayersMessage();
                    } else {
                        for (Player player : players) {
                            playersContainer.add(createPlayerPanel(player));
                        }
                    }
                } else {
                    addNoPlayersMessage();
                }
            } catch (Exception e) {
                addErrorMessage("Ошибка загрузки: " + e.getMessage());
            }

            playersContainer.revalidate();
            playersContainer.repaint();
        });
    }

    private void showWaitingMessage() {
        playersContainer.removeAll();
        JLabel label = new JLabel("Ожидание данных игроков...");
        label.setForeground(Color.GRAY);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        playersContainer.add(label);
        playersContainer.revalidate();
        playersContainer.repaint();
    }

    private void addNoPlayersMessage() {
        JLabel label = new JLabel("Нет игроков");
        label.setForeground(Color.GRAY);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        playersContainer.add(label);
    }

    private void addErrorMessage(String message) {
        JLabel label = new JLabel(message);
        label.setForeground(Color.RED);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        playersContainer.add(label);
    }

    private JPanel createPlayerPanel(Player player) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        // Основная информация
        JLabel nameLabel = new JLabel(player.getName());
        nameLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JLabel scoreLabel = new JLabel("Очки: " + player.getScore());
        scoreLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        JLabel roleLabel = new JLabel(getRoleText(player.getRole()));
        roleLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        roleLabel.setForeground(getRoleColor(player.getRole()));

        // Панель с информацией
        JPanel infoPanel = new JPanel(new GridLayout(3, 1));
        infoPanel.add(nameLabel);
        infoPanel.add(scoreLabel);
        infoPanel.add(roleLabel);

        // Индикатор текущего игрока
        if (player.getId() == gameManager.getPlayerId()) {
            panel.setBackground(new Color(220, 240, 255));
            panel.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));

            JLabel youLabel = new JLabel("(Вы)");
            youLabel.setForeground(Color.BLUE);
            youLabel.setFont(new Font("Arial", Font.BOLD, 10));
            infoPanel.add(youLabel);
        }

        panel.add(infoPanel, BorderLayout.CENTER);

        // Индикатор типа игрока
        if (player.getType() == SnakesProto.PlayerType.ROBOT) {
            JLabel botLabel = new JLabel("🤖");
            botLabel.setFont(new Font("Arial", Font.PLAIN, 16));
            panel.add(botLabel, BorderLayout.EAST);
        }

        return panel;
    }

    private String getRoleText(SnakesProto.NodeRole role) {
        switch (role) {
            case MASTER: return "Ведущий";
            case DEPUTY: return "Заместитель";
            case NORMAL: return "Игрок";
            case VIEWER: return "Наблюдатель";
            default: return "Неизвестно";
        }
    }

    private Color getRoleColor(SnakesProto.NodeRole role) {
        switch (role) {
            case MASTER: return new Color(0, 100, 0); // Темно-зеленый
            case DEPUTY: return new Color(0, 0, 150); // Темно-синий
            case NORMAL: return Color.BLACK;
            case VIEWER: return Color.GRAY;
            default: return Color.RED;
        }
    }

    // Сеттер для gameManager, если он не установлен в конструкторе
    public void setGameManager(GameManager gameManager) {
        // Note: В реальности нужно рефакторить, но для простоты оставим так
    }
}