package org.example.snake.game;

import org.example.snake.config.GameConfig;
import org.example.snake.entities.*;
import org.example.snake.protocol.SnakesProto;

import java.util.*;

public class GameEngine {
    private GameState state;
    private final int width;
    private final int height;
    private final Random random = new Random();
    private final List<Coordinate> deathFood = new ArrayList<>();
    private final int foodStatic;

    public GameEngine(GameConfig config) {
        this.width = config.getWidth();
        this.height = config.getHeight();
        this.state = new GameState();
        this.foodStatic = config.getFoodStatic();
    }

    public List<Player> update(Map<Integer, SnakesProto.Direction> directionUpdates) {
        applyDirectionUpdates(directionUpdates);

        moveSnakes();

        checkFood();
        state.getSnakes().forEach(Snake::moveTail);
        for (var food: state.getFoods()){
            if (food.isEaten())
                state.removeFood(food);
        }

        checkCollisions();
        var deads = new ArrayList<Player>();
        for(var snake: state.getSnakes()){
            if (snake.isDead()) {
                state.getPlayer(snake.getPlayerId()).setRole(SnakesProto.NodeRole.VIEWER);
                deads.add(state.getPlayer(snake.getPlayerId()));
                state.removeSnake(snake);
            }
        }

        for (var coord: deathFood) {
            if (isCellEmpty(coord))
                state.addFood(new Food(coord));
        }
        deathFood.clear();

        spawnFood();
        state.incrementStateOrder();

        return deads;
    }

    public boolean addPlayer(Player player) {
        state.addPlayer(player);

        Coordinate spawnPoint = findSpawnPoint();
        if (spawnPoint == null) return false;

        if (player.getRole() != SnakesProto.NodeRole.VIEWER) {
            Snake snake = Snake.createNewSnake(player.getId(), spawnPoint);
            state.addSnake(snake);
        }

        return true;
    }

    public void removePlayer(int playerId) {
        state.removePlayer(state.getPlayer(playerId));
        Snake snake = state.getSnakeByPlayerId(playerId);
        if (snake != null) {
            snake.zombie();
        }
    }

    public GameState getState() {
        return state;
    }

    private void applyDirectionUpdates(Map<Integer, SnakesProto.Direction> directionUpdates) {
        for (Map.Entry<Integer, SnakesProto.Direction> entry : directionUpdates.entrySet()) {
            Snake snake = state.getSnakeByPlayerId(entry.getKey());
            if (snake != null) {
                Direction newDirection = Direction.fromProtobuf(entry.getValue());
                snake.requestDirection(newDirection);
            }
        }
    }

    private void moveSnakes() {
        for (Snake snake : state.getSnakes()) {
            snake.moveHead(width, height);
        }
    }

    private void checkFood() {
        for (var food: state.getFoods()){
            for (var snake: state.getSnakes()){
                if (snake.getState() == SnakesProto.GameState.Snake.SnakeState.ZOMBIE)
                    continue;

                if (snake.collision(food.getCoordinate(), width, height)){
                    snake.eat();
                    food.eat();
                    state.getPlayer(snake.getPlayerId()).incrementScore();
                }
            }
        }
    }

    private void checkCollisions() {
        List<Snake> snakes = new ArrayList<>(state.getSnakes());

        for (int i = 0; i < snakes.size(); i++) {
            Snake snake1 = snakes.get(i);
            if (snake1.getState() != SnakesProto.GameState.Snake.SnakeState.ALIVE) continue;

            Coordinate head = snake1.getHeadAbsolute();

            if (snake1.collision(width, height)) {
                handleSnakeDeath(snake1, snake1);
                continue;
            }

            for (int j = 0; j < snakes.size(); j++) {
                if (i == j) continue;
                Snake snake2 = snakes.get(j);
                if (snake2.collision(head, width, height)) {
                    handleSnakeDeath(snake1, snake2);
                    break;
                }
            }
        }
    }

    private void handleSnakeDeath(Snake deadSnake, Snake killerSnake) {
        deadSnake.kill();

        if (killerSnake != deadSnake && killerSnake.getState() == SnakesProto.GameState.Snake.SnakeState.ALIVE) {
            state.getPlayer(killerSnake.getPlayerId()).incrementScore();
        }

        List<Coordinate> bodyCells = deadSnake.getAllCellsAbsolute(width, height);
        for (Coordinate coord : bodyCells) {
            if (random.nextDouble() < 0.5) {
                deathFood.add(coord);
            }
        }
    }

    private void spawnFood() {
        int targetFood = foodStatic + countAliveSnakes();
        int currentFood = state.getFoods().size();

        if (currentFood < targetFood) {
            List<Coordinate> emptyCells = getEmptyCells();
            Collections.shuffle(emptyCells);

            int toSpawn = Math.min(targetFood - currentFood, emptyCells.size());
            for (int i = 0; i < toSpawn; i++) {
                Coordinate spawnCoord = emptyCells.get(i);
                if (isCellEmpty(spawnCoord)) {
                    state.addFood(new Food(spawnCoord));
                }
            }
        }
    }

    private boolean canSpawnNewSnake() {
        return findSpawnPoint() != null;
    }

    private Coordinate findSpawnPoint() {
        for (int x = 2; x < width - 2; x++) {
            for (int y = 2; y < height - 2; y++) {
                if (isAreaEmpty(x, y, 5)) {
                    return new Coordinate(x, y);
                }
            }
        }
        return null;
    }

    private boolean isAreaEmpty(int centerX, int centerY, int size) {
        int radius = size / 2;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                Coordinate coord = new Coordinate(
                        Math.floorMod(x, width),
                        Math.floorMod(y, height)
                );
                if (!isCellEmpty(coord)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isCellEmpty(Coordinate coord) {
        for (Snake snake : state.getSnakes()) {
            if (snake.collision(coord, width, height)) {
                return false;
            }
        }
        for (Food food : state.getFoods()) {
            if (food.getCoordinate().equals(coord)) {
                return false;
            }
        }
        return true;
    }

    private List<Coordinate> getEmptyCells() {
        List<Coordinate> empty = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Coordinate coord = new Coordinate(x, y);
                if (isCellEmpty(coord)) {
                    empty.add(coord);
                }
            }
        }
        return empty;
    }

    private int countAliveSnakes() {
        return (int) state.getSnakes().stream()
                .filter(snake -> snake.getState() == SnakesProto.GameState.Snake.SnakeState.ALIVE)
                .count();
    }

    public boolean canAcceptNewPlayers() {
        return findSpawnPoint() != null;
    }

    public void setState(GameState state) {
        this.state = state;
    }
}