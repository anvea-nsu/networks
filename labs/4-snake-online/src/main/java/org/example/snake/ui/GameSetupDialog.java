package org.example.snake.ui;

import org.example.snake.game.GameManager;
import org.example.snake.config.GameConfig;

import javax.swing.*;
import java.awt.*;

public class GameSetupDialog extends JDialog {
    private boolean confirmed = false;
    private JTextField gameNameField;
    private JTextField playerNameField;
    private JSpinner widthSpinner;
    private JSpinner heightSpinner;
    private JSpinner foodSpinner;
    private JSpinner delaySpinner;

    public GameSetupDialog(Frame parent, GameManager gameManager) {
        super(parent, "Создание новой игры", true);
        initializeUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Основная панель
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Панель настроек
        JPanel settingsPanel = new JPanel(new GridLayout(6, 2, 10, 10));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Настройки игры"));

        // Поля ввода
        gameNameField = new JTextField("Моя игра");
        playerNameField = new JTextField("Игрок");
        widthSpinner = new JSpinner(new SpinnerNumberModel(40, 10, 100, 5));
        heightSpinner = new JSpinner(new SpinnerNumberModel(30, 10, 100, 5));
        foodSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        delaySpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 3000, 100));

        // Добавляем компоненты
        settingsPanel.add(new JLabel("Название игры:"));
        settingsPanel.add(gameNameField);
        settingsPanel.add(new JLabel("Ваше имя:"));
        settingsPanel.add(playerNameField);
        settingsPanel.add(new JLabel("Ширина поля:"));
        settingsPanel.add(widthSpinner);
        settingsPanel.add(new JLabel("Высота поля:"));
        settingsPanel.add(heightSpinner);
        settingsPanel.add(new JLabel("Базовая еда:"));
        settingsPanel.add(foodSpinner);
        settingsPanel.add(new JLabel("Скорость (мс):"));
        settingsPanel.add(delaySpinner);

        mainPanel.add(settingsPanel, BorderLayout.CENTER);

        // Панель кнопок
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton createButton = new JButton("Создать игру");
        JButton cancelButton = new JButton("Отмена");

        createButton.addActionListener(e -> {
            if (validateInput()) {
                confirmed = true;
                dispose();
            }
        });

        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(cancelButton);
        buttonPanel.add(createButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private boolean validateInput() {
        String gameName = gameNameField.getText().trim();
        String playerName = playerNameField.getText().trim();

        if (gameName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите название игры", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (playerName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите ваше имя", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getGameName() {
        return gameNameField.getText().trim();
    }

    public String getPlayerName() {
        return playerNameField.getText().trim();
    }

    public GameConfig getGameConfig() {
        return new GameConfig(
                (Integer) widthSpinner.getValue(),
                (Integer) heightSpinner.getValue(),
                (Integer) foodSpinner.getValue(),
                (Integer) delaySpinner.getValue()
        );
    }
}