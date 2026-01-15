package org.example.snake.ui;

import org.example.snake.game.GameManager;
import org.example.snake.game.IGameListener;
import org.example.snake.protocol.SnakesProto;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class MainFrame extends JFrame implements IGameListener {
    private final GameManager gameManager;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    private MenuPanel menuPanel;
    private GamePanel gamePanel;

    public MainFrame(GameManager gameManager) {
        this.gameManager = gameManager;
        gameManager.setListener(this);
        initializeUI();
        setupKeyBindings();
    }

    private void initializeUI() {
        setTitle("Многопользовательская Змейка");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        menuPanel = new MenuPanel(gameManager, this);
        gamePanel = new GamePanel(gameManager, this);

        mainPanel.add(menuPanel, "MENU");
        mainPanel.add(gamePanel, "GAME");

        add(mainPanel);
        showMenu();
    }

    public void showMenu() {
        cardLayout.show(mainPanel, "MENU");
        menuPanel.refreshGameList();
        if (gamePanel != null) {
            gamePanel.stopGameLoop();
        }
    }

    public void showGame() {
        cardLayout.show(mainPanel, "GAME");
        if (gamePanel != null) {
            gamePanel.startGameLoop();
        }
    }

    private void setupKeyBindings() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        // Управление WASD
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "UP");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "DOWN");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "LEFT");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "RIGHT");

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "UP");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "DOWN");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "LEFT");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "RIGHT");

        actionMap.put("UP", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gameManager != null) {
                    gameManager.sendSteerCommand(SnakesProto.Direction.UP);
                }
            }
        });

        actionMap.put("DOWN", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gameManager != null) {
                    gameManager.sendSteerCommand(SnakesProto.Direction.DOWN);
                }
            }
        });

        actionMap.put("LEFT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gameManager != null) {
                    gameManager.sendSteerCommand(SnakesProto.Direction.LEFT);
                }
            }
        });

        actionMap.put("RIGHT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gameManager != null) {
                    gameManager.sendSteerCommand(SnakesProto.Direction.RIGHT);
                }
            }
        });

        // ESC для выхода в меню
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "MENU");
        actionMap.put("MENU", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gameManager != null && gameManager.isGameRunning()) {
                    int result = JOptionPane.showConfirmDialog(
                            MainFrame.this,
                            "Выйти в главное меню?",
                            "Подтверждение",
                            JOptionPane.YES_NO_OPTION
                    );
                    if (result == JOptionPane.YES_OPTION) {
                        showMenu();
                    }
                }
            }
        });
    }

    public void showError(String message){
        JOptionPane.showMessageDialog(this, message);
    }

    @Override
    public void leaveGame() {
        showMenu();
    }
}