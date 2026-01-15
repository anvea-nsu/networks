package org.example.snake.ui;

import org.example.snake.game.GameManager;
import org.example.snake.DiscoveredGame;
import org.example.snake.protocol.SnakesProto;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class MenuPanel extends JPanel {
    private final GameManager gameManager;
    private final MainFrame mainFrame;

    private DefaultListModel<DiscoveredGame> gameListModel;
    private JList<DiscoveredGame> gameList;
    private Timer refreshTimer;
    private JButton joinButton;
    private JButton joinViewerButton;
    private JLabel statusLabel;

    public MenuPanel(GameManager gameManager, MainFrame mainFrame) {
        this.gameManager = gameManager;
        this.mainFrame = mainFrame;
        initializeUI();
        startAutoRefresh();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(240, 240, 240));

        JLabel titleLabel = new JLabel("МНОГОПОЛЬЗОВАТЕЛЬСКАЯ ЗМЕЙКА", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(0, 82, 165));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        add(titleLabel, BorderLayout.NORTH);

        JPanel mainContentPanel = new JPanel(new BorderLayout(15, 15));
        mainContentPanel.setBackground(new Color(240, 240, 240));

        JPanel gamesPanel = createGamesPanel();
        mainContentPanel.add(gamesPanel, BorderLayout.CENTER);

        JPanel controlPanel = createControlPanel();
        mainContentPanel.add(controlPanel, BorderLayout.SOUTH);

        add(mainContentPanel, BorderLayout.CENTER);

        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
    }

    private JPanel createGamesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(150, 150, 150), 2),
                "Доступные игры в сети",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14),
                new Color(0, 82, 165)
        ));
        panel.setBackground(Color.WHITE);

        gameListModel = new DefaultListModel<>();
        gameList = new JList<>(gameListModel);
        gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gameList.setCellRenderer(new GameListRenderer());
        gameList.setFixedCellHeight(70); // Увеличиваем высоту строк для лучшего отображения
        gameList.setBackground(Color.WHITE);

        gameList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    joinGame(null);
                }
                updateButtonStates();
            }
        });

        gameList.addListSelectionListener(e -> updateButtonStates());

        JScrollPane scrollPane = new JScrollPane(gameList);
        scrollPane.setPreferredSize(new Dimension(500, 350));
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel infoPanel = createGameInfoPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, infoPanel);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerLocation(250);

        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createGameInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Информация о выбранной игре"));
        panel.setBackground(Color.WHITE);

        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setBackground(new Color(248, 248, 248));
        infoArea.setFont(new Font("Arial", Font.PLAIN, 12));
        infoArea.setText("Выберите игру из списка для просмотра информации");

        gameList.addListSelectionListener(e -> {
            DiscoveredGame selected = gameList.getSelectedValue();
            if (selected != null && selected.getMasterAddress() != null) {
                infoArea.setText(String.format(
                        "Название: %s\n" +
                                "Адрес хоста: %s\n" +
                                "Количество игроков: %d\n" +
                                "Статус: %s",
                        selected.getGameName(),
                        selected.getMasterAddress(),
                        selected.getPlayerCount(),
                        selected.getPlayerCount() >= 4 ? "Заполнена" : "Есть места"
                ));
            } else {
                infoArea.setText("Выберите игру из списка для просмотра информации");
            }
        });

        JScrollPane infoScroll = new JScrollPane(infoArea);
        infoScroll.setPreferredSize(new Dimension(300, 100));
        panel.add(infoScroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        panel.setBackground(new Color(240, 240, 240));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        joinButton = createStyledButton("Присоединиться к игре", new Color(46, 125, 50));
        joinViewerButton = createStyledButton("Только наблюдать", new Color(2, 119, 189));
        JButton createButton = createStyledButton("Создать новую игру", new Color(198, 40, 40));
        JButton refreshButton = createStyledButton("Обновить список", new Color(101, 31, 255));

        joinButton.addActionListener(this::joinGame);
        joinViewerButton.addActionListener(this::joinAsViewer);
        createButton.addActionListener(this::createGame);
        refreshButton.addActionListener(e -> refreshGameList());

        joinButton.setEnabled(false);
        joinViewerButton.setEnabled(false);

        panel.add(createButton);
        panel.add(joinButton);
        panel.add(joinViewerButton);
        panel.add(refreshButton);

        return panel;
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 2),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(color.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(color);
            }
        });

        return button;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBackground(new Color(240, 240, 240));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        statusLabel = new JLabel("Поиск игр в сети...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setForeground(Color.DARK_GRAY);

        panel.add(statusLabel);

        return panel;
    }

    private void updateButtonStates() {
        DiscoveredGame selectedGame = gameList.getSelectedValue();
        boolean hasValidSelection = selectedGame != null && selectedGame.getMasterAddress() != null;

        joinButton.setEnabled(hasValidSelection);
        joinViewerButton.setEnabled(hasValidSelection);

        if (hasValidSelection) {
            joinButton.setToolTipText("Присоединиться к игре '" + selectedGame.getGameName() + "'");
            joinViewerButton.setToolTipText("Наблюдать за игрой '" + selectedGame.getGameName() + "'");
        } else {
            joinButton.setToolTipText("Выберите игру для присоединения");
            joinViewerButton.setToolTipText("Выберите игру для наблюдения");
        }
    }

    public void refreshGameList() {
        List<DiscoveredGame> games = gameManager.getDiscoveredGames();
        gameListModel.clear();

        if (games.isEmpty()) {
//            gameListModel.addElement(new DiscoveredGame("Игры не найдены", "Обновите список или создайте новую игру", null, 0));
            statusLabel.setText("Игры не найдены. Убедитесь, что игры запущены в сети.");
            statusLabel.setForeground(Color.RED);
        } else {
            for (DiscoveredGame game : games) {
                gameListModel.addElement(game);
            }
            statusLabel.setText("Найдено игр: " + games.size() + " | Выберите игру для присоединения");
            statusLabel.setForeground(new Color(0, 100, 0));
        }

        updateButtonStates();
    }

    private void startAutoRefresh() {
        refreshTimer = new Timer(3000, e -> {
            refreshGameList();
            if (gameListModel.size() > 0 && !gameListModel.getElementAt(0).getGameName().equals("Игры не найдены")) {
                statusLabel.setText("Список игр обновлен автоматически | Найдено: " + gameListModel.size());
            }
        });
        refreshTimer.start();
    }

    private void createGame(ActionEvent e) {
        try {
            GameSetupDialog setupDialog = new GameSetupDialog(mainFrame, gameManager);
            setupDialog.setVisible(true);

            if (setupDialog.isConfirmed()) {
                gameManager.createNewGame(setupDialog.getGameConfig());
                mainFrame.showGame();
            }
        } catch (Exception ex) {
            mainFrame.showError("Ошибка создания игры: " + ex.getMessage());
        }
    }

    private void joinGame(ActionEvent e) {
        DiscoveredGame selectedGame = gameList.getSelectedValue();
        if (selectedGame == null || selectedGame.getMasterAddress() == null) {
            JOptionPane.showMessageDialog(this,
                    "Выберите игру из списка", "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }

        statusLabel.setText("Присоединение к игре...");
        statusLabel.setForeground(Color.BLUE);
        joinButton.setEnabled(false);
        joinViewerButton.setEnabled(false);

        try {
            gameManager.joinGame(selectedGame, SnakesProto.NodeRole.NORMAL);
            mainFrame.showGame();
//            Timer joinTimer = new Timer(500, evt -> {
//                if (gameManager.isGameRunning()) {
//                    ((Timer)evt.getSource()).stop();
//                    statusLabel.setText("Успешно присоединились!");
//                    statusLabel.setForeground(new Color(0, 150, 0));
//                    mainFrame.showGame();
//                } else if (!gameManager.isWaitingForJoinAck()) {
//                    ((Timer)evt.getSource()).stop();
//                    statusLabel.setText("Ошибка присоединения");
//                    statusLabel.setForeground(Color.RED);
//                    joinButton.setEnabled(true);
//                    joinViewerButton.setEnabled(true);
//                }
//            });
//            joinTimer.start();

        } catch (Exception ex) {
            statusLabel.setText("Ошибка: " + ex.getMessage());
            statusLabel.setForeground(Color.RED);
            joinButton.setEnabled(true);
            joinViewerButton.setEnabled(true);
            JOptionPane.showMessageDialog(this,
                    "Ошибка присоединения: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void joinAsViewer(ActionEvent e) {
        DiscoveredGame selectedGame = gameList.getSelectedValue();
        if (selectedGame == null || selectedGame.getMasterAddress() == null) {
            mainFrame.showError("Выберите игру из списка для наблюдения");
            return;
        }

        try {
            gameManager.joinGame(selectedGame, SnakesProto.NodeRole.VIEWER);
            mainFrame.showGame();
        } catch (Exception ex) {
            mainFrame.showError("Ошибка присоединения как наблюдатель: " + ex.getMessage());
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshGameList();
            if (refreshTimer != null && !refreshTimer.isRunning()) {
                refreshTimer.start();
            }
        } else {
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
        }
    }

    private static class GameListRenderer extends JPanel implements ListCellRenderer<DiscoveredGame> {
        private JCheckBox checkBox;
        private JLabel gameNameLabel;
        private JLabel detailsLabel;
        private JLabel statusLabel;

        public GameListRenderer() {
            setLayout(new BorderLayout(5, 5));
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            setOpaque(true);

            // Чекбокс
            checkBox = new JCheckBox();
            checkBox.setOpaque(false);
            checkBox.setEnabled(false); // Делаем неактивным, так как выбор осуществляется кликом по строке

            // Основная информация об игре
            JPanel infoPanel = new JPanel(new GridLayout(2, 1));
            infoPanel.setOpaque(false);

            gameNameLabel = new JLabel();
            gameNameLabel.setFont(new Font("Arial", Font.BOLD, 14));
            gameNameLabel.setForeground(new Color(0, 82, 165));

            detailsLabel = new JLabel();
            detailsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
            detailsLabel.setForeground(Color.DARK_GRAY);

            infoPanel.add(gameNameLabel);
            infoPanel.add(detailsLabel);

            // Статус игры
            statusLabel = new JLabel();
            statusLabel.setFont(new Font("Arial", Font.BOLD, 10));
            statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            add(checkBox, BorderLayout.WEST);
            add(infoPanel, BorderLayout.CENTER);
            add(statusLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DiscoveredGame> list,
                                                      DiscoveredGame game,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {

            // Устанавливаем данные
            gameNameLabel.setText(game.getGameName());

            if (game.getMasterAddress() != null) {
                detailsLabel.setText(String.format("Игроков: %d | Хост: %s",
                        game.getPlayerCount(),
                        game.getMasterAddress().getAddress().getHostAddress()));

                // Определяем статус игры
                if (game.getPlayerCount() >= 6) {
                    statusLabel.setText("ЗАПОЛНЕНА");
                    statusLabel.setForeground(Color.RED);
                } else if (game.getPlayerCount() >= 4) {
                    statusLabel.setText("МАЛО МЕСТ");
                    statusLabel.setForeground(Color.ORANGE);
                } else {
                    statusLabel.setText("ОТКРЫТА");
                    statusLabel.setForeground(new Color(0, 150, 0));
                }
            } else {
                detailsLabel.setText(game.getDescription() != null ? game.getDescription() : "Недоступна");
                statusLabel.setText("НЕТ ДАННЫХ");
                statusLabel.setForeground(Color.GRAY);
            }

            // Визуальное выделение выбранного элемента
            if (isSelected) {
                setBackground(new Color(220, 240, 255));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0, 120, 215), 2),
                        BorderFactory.createEmptyBorder(3, 3, 3, 3)
                ));
                checkBox.setSelected(true);
            } else {
                setBackground(index % 2 == 0 ? Color.WHITE : new Color(248, 248, 248));
                setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                checkBox.setSelected(false);
            }

            return this;
        }
    }
}