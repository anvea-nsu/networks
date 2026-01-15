package org.example.snake.ui;

import org.example.snake.game.GameManager;
import org.example.snake.protocol.SnakesProto;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GamePanel extends JPanel {
    private final GameManager gameManager;
    private final MainFrame mainFrame;

    private GameFieldPanel gameFieldPanel;
    private PlayersPanel playersPanel;
    private JLabel statusLabel;

    private ScheduledExecutorService gameLoop;
    private static final int UI_UPDATE_RATE = 60; // FPS

    public GamePanel(GameManager gameManager, MainFrame mainFrame) {
        this.gameManager = gameManager;
        this.mainFrame = mainFrame;
        setDoubleBuffered(true);
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Панель статуса
        statusLabel = new JLabel("Ожидание начала игры...", JLabel.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(getBackground());
        add(statusLabel, BorderLayout.NORTH);

        // Игровое поле
        gameFieldPanel = new GameFieldPanel(gameManager);
        gameFieldPanel.setDoubleBuffered(true);

        // Используем GridBagLayout для центрирования и растягивания
        JPanel gameContainer = new JPanel(new GridBagLayout());
        gameContainer.setBackground(Color.BLACK);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gameContainer.add(gameFieldPanel, gbc);

        add(gameContainer, BorderLayout.CENTER);

        // Панель игроков
        playersPanel = new PlayersPanel(gameManager);
        playersPanel.setDoubleBuffered(true);
        JScrollPane scrollPane = new JScrollPane(playersPanel);
        scrollPane.setPreferredSize(new Dimension(200, 0));
        add(scrollPane, BorderLayout.EAST);

        JButton exitButton = new JButton("Выйти в меню");
        exitButton.addActionListener(e -> {
            gameManager.leaveGame();
            mainFrame.showMenu();
        });

        JPanel southPanel = new JPanel(new FlowLayout());
        southPanel.add(exitButton);
        add(southPanel, BorderLayout.SOUTH);
    }

    public void startGameLoop() {
        if (gameLoop != null && !gameLoop.isShutdown()) {
            return;
        }

        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleAtFixedRate(this::updateUI, 0, 1000 / UI_UPDATE_RATE, TimeUnit.MILLISECONDS);
    }

    public void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.shutdownNow();
            try {
                gameLoop.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            gameLoop = null;
        }
    }

    public void updateUI() {
        SwingUtilities.invokeLater(() -> {
            try {
                if (gameManager == null || !gameManager.isGameRunning()) {
                    showWaitingMessage();
                    return;
                }

                updateStatus();
                gameFieldPanel.repaint();
                playersPanel.updatePlayers();
            } catch (Exception e) {
                System.err.println("UI update error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void showWaitingMessage() {
        if (statusLabel != null) {
            statusLabel.setText("Ожидание начала игры...");
        }
    }

    private void updateStatus() {
        if (statusLabel != null) {
            String role = getRoleText(gameManager.getCurrentRole());
            String status = String.format("Роль: %s | Игрок: %s", role, gameManager.getPlayerName());
            statusLabel.setText(status);
        }
    }

    private String getRoleText(SnakesProto.NodeRole role) {
        if (role == null) {
            return "Неизвестно";
        }

        return switch (role) {
            case MASTER -> "Ведущий";
            case DEPUTY -> "Заместитель";
            case NORMAL -> "Игрок";
            case VIEWER -> "Наблюдатель";
        };
    }
}