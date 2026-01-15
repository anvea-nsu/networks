package org.example.snake.ui;

import org.example.snake.game.GameManager;
import org.example.snake.config.GameConfig;
import org.example.snake.entities.Coordinate;
import org.example.snake.entities.Snake;
import org.example.snake.protocol.SnakesProto;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class GameFieldPanel extends JPanel {
    private final GameManager gameManager;
    private int cellSize = 20; // Начальный размер ячейки

    // Цвета для разных игроков
    private final Color[] snakeColors = {
            new Color(0, 128, 0),    // Зеленый
            new Color(0, 0, 255),    // Синий
            new Color(255, 0, 0),    // Красный
            new Color(255, 165, 0),  // Оранжевый
            new Color(128, 0, 128),  // Фиолетовый
            new Color(0, 128, 128),  // Бирюзовый
            new Color(255, 192, 203), // Розовый
            new Color(165, 42, 42)   // Коричневый
    };

    public GameFieldPanel(GameManager gameManager) {
        this.gameManager = gameManager;
        setBackground(Color.WHITE); // Изменили фон на белый
        setDoubleBuffered(true);
        setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        try {
            // Проверяем, есть ли конфигурация
            if (gameManager.getConfig() == null) {
                drawWaitingMessage(g2d);
                return;
            }

            SnakesProto.GameState gameState = gameManager.getGameState();
            if (gameState != null) {
                drawField(g2d, gameState);
            } else {
                drawWaitingMessage(g2d);
            }
        } catch (Exception e) {
            drawErrorMessage(g2d, e.getMessage());
        }
    }

    private void drawField(Graphics2D g2d, SnakesProto.GameState gameState) {
        // Получаем конфигурацию
        var config = gameManager.getConfig();
        if (config == null) {
            drawWaitingMessage(g2d);
            return;
        }

        // Вычисляем оптимальный размер ячейки для всего экрана
        calculateCellSize(config);

        // Вычисляем смещение для центрирования
        int fieldWidth = config.getWidth() * cellSize;
        int fieldHeight = config.getHeight() * cellSize;
        int offsetX = (getWidth() - fieldWidth) / 2;
        int offsetY = (getHeight() - fieldHeight) / 2;

        // Рисуем темный фон для игрового поля
        g2d.setColor(new Color(20, 20, 20));
        g2d.fillRect(offsetX, offsetY, fieldWidth, fieldHeight);

        // Рисуем сетку
        drawGrid(g2d, config, offsetX, offsetY);

        // Рисуем еду
        drawFood(g2d, gameState, offsetX, offsetY);

        // Рисуем змей
        drawSnakes(g2d, gameState, offsetX, offsetY);
    }

    private void calculateCellSize(GameConfig config) {
        int width = config.getWidth();
        int height = config.getHeight();

        // Вычисляем максимальный размер ячейки, чтобы поле поместилось
        int maxCellWidth = getWidth() / width;
        int maxCellHeight = getHeight() / height;

        cellSize = Math.min(maxCellWidth, maxCellHeight);
        // Минимальный размер ячейки - 5 пикселей
        cellSize = Math.max(cellSize, 5);
    }

    private void drawGrid(Graphics2D g2d, GameConfig config, int offsetX, int offsetY) {
        g2d.setColor(new Color(80, 80, 80)); // Сделали сетку немного светлее

        int width = config.getWidth();
        int height = config.getHeight();

        for (int x = 0; x <= width; x++) {
            int xPos = offsetX + x * cellSize;
            g2d.drawLine(xPos, offsetY, xPos, offsetY + height * cellSize);
        }

        for (int y = 0; y <= height; y++) {
            int yPos = offsetY + y * cellSize;
            g2d.drawLine(offsetX, yPos, offsetX + width * cellSize, yPos);
        }
    }

    private void drawFood(Graphics2D g2d, SnakesProto.GameState gameState, int offsetX, int offsetY) {
        g2d.setColor(Color.YELLOW);

        for (SnakesProto.GameState.Coord foodCoord : gameState.getFoodsList()) {
            int x = offsetX + foodCoord.getX() * cellSize;
            int y = offsetY + foodCoord.getY() * cellSize;

            g2d.fillOval(x + 2, y + 2, cellSize - 4, cellSize - 4);
        }
    }

    private void drawSnakes(Graphics2D g2d, SnakesProto.GameState gameState, int offsetX, int offsetY) {
        Map<Integer, Color> playerColors = new HashMap<>();
        AtomicInteger colorIndex = new AtomicInteger();

        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            int playerId = snake.getPlayerId();
            Color snakeColor = playerColors.computeIfAbsent(playerId,
                    k -> snakeColors[colorIndex.getAndIncrement() % snakeColors.length]);

            drawSnake(g2d, snake, snakeColor, playerId == gameManager.getPlayerId(), offsetX, offsetY);
        }
    }

    private void drawSnake(Graphics2D g2d, SnakesProto.GameState.Snake snake, Color color, boolean isMySnake, int offsetX, int offsetY) {
        if (snake.getPointsCount() < 2) return;

        // Восстанавливаем абсолютные координаты
        List<Coordinate> absoluteCoords = getAbsoluteCoordinates(snake);

        // Рисуем тело змейки
        g2d.setColor(color);
        for (Coordinate coord : absoluteCoords) {
            int x = offsetX + coord.getX() * cellSize;
            int y = offsetY + coord.getY() * cellSize;
            g2d.fillRect(x + 1, y + 1, cellSize - 2, cellSize - 2);
        }

        // Рисуем голову
        if (!absoluteCoords.isEmpty()) {
            Coordinate head = absoluteCoords.get(0);
            int x = offsetX + head.getX() * cellSize;
            int y = offsetY + head.getY() * cellSize;

            // Голова выделяется
            g2d.setColor(isMySnake ? Color.WHITE : color.brighter());
            g2d.fillRect(x, y, cellSize, cellSize);

            // Глаза
            g2d.setColor(Color.BLACK);
            drawEyes(g2d, x, y, snake.getHeadDirection());
        }

        // Если змейка мертва - рисуем крестик
        if (snake.getState() == SnakesProto.GameState.Snake.SnakeState.ZOMBIE) {
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(2));
            for (Coordinate coord : absoluteCoords) {
                int x = offsetX + coord.getX() * cellSize;
                int y = offsetY + coord.getY() * cellSize;

                g2d.drawLine(x + 2, y + 2, x + cellSize - 2, y + cellSize - 2);
                g2d.drawLine(x + cellSize - 2, y + 2, x + 2, y + cellSize - 2);
            }
        }
    }

    private List<Coordinate> getAbsoluteCoordinates(SnakesProto.GameState.Snake snake) {
        // Создаем временную змейку для получения абсолютных координат
        Snake tempSnake = Snake.fromProtobuf(snake);
        return tempSnake.getAllCellsAbsolute(
                gameManager.getConfig().getWidth(),
                gameManager.getConfig().getHeight()
        );
    }

    private void drawEyes(Graphics2D g2d, int x, int y, SnakesProto.Direction direction) {
        int eyeSize = cellSize / 4;
        int offset = cellSize / 4;

        switch (direction) {
            case UP:
                g2d.fillOval(x + offset, y + offset, eyeSize, eyeSize);
                g2d.fillOval(x + cellSize - offset - eyeSize, y + offset, eyeSize, eyeSize);
                break;
            case DOWN:
                g2d.fillOval(x + offset, y + cellSize - offset - eyeSize, eyeSize, eyeSize);
                g2d.fillOval(x + cellSize - offset - eyeSize, y + cellSize - offset - eyeSize, eyeSize, eyeSize);
                break;
            case LEFT:
                g2d.fillOval(x + offset, y + offset, eyeSize, eyeSize);
                g2d.fillOval(x + offset, y + cellSize - offset - eyeSize, eyeSize, eyeSize);
                break;
            case RIGHT:
                g2d.fillOval(x + cellSize - offset - eyeSize, y + offset, eyeSize, eyeSize);
                g2d.fillOval(x + cellSize - offset - eyeSize, y + cellSize - offset - eyeSize, eyeSize, eyeSize);
                break;
        }
    }

    private void drawWaitingMessage(Graphics2D g2d) {
        g2d.setColor(Color.BLACK); // Черный текст на белом фоне
        g2d.setFont(new Font("Arial", Font.BOLD, 20));

        String message = "Ожидание начала игры...";
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(message)) / 2;
        int y = getHeight() / 2;

        g2d.drawString(message, x, y);
    }

    private void drawErrorMessage(Graphics2D g2d, String error) {
        g2d.setColor(Color.RED);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));

        String message = "Ошибка: " + error;
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(message)) / 2;
        int y = getHeight() / 2;

        g2d.drawString(message, x, y);
    }
}