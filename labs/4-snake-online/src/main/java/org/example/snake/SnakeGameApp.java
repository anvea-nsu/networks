package org.example.snake;

import org.example.snake.game.GameManager;
import org.example.snake.ui.MainFrame;

import javax.swing.*;

public class SnakeGameApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String playerName = JOptionPane.showInputDialog("Введите ваше имя:");
                if (playerName == null || playerName.trim().isEmpty()) {
                    playerName = "Player_" + System.currentTimeMillis() % 1000;
                }

                GameManager gameManager = new GameManager(playerName);

                MainFrame mainFrame = new MainFrame(gameManager);
                mainFrame.setVisible(true);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Ошибка запуска: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}