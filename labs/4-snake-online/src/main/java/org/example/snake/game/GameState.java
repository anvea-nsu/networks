package org.example.snake.game;

import org.example.snake.config.GameConfig;
import org.example.snake.entities.Coordinate;
import org.example.snake.entities.Food;
import org.example.snake.entities.Player;
import org.example.snake.entities.Snake;
import org.example.snake.protocol.SnakesProto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameState {
    private final List<Snake> snakes;
    private final List<Food> foods;
    private final List<Player> players;
    private int stateOrder;

    public GameState() {
        this.snakes = new CopyOnWriteArrayList<>();
        this.foods = new CopyOnWriteArrayList<>();
        this.players = new CopyOnWriteArrayList<>();
        this.stateOrder = 0;
    }

    public static GameState fromProtobuf(SnakesProto.GameState protoState) {
        GameState gameState = new GameState();
        gameState.stateOrder = protoState.getStateOrder();

        for (SnakesProto.GameState.Snake protoSnake : protoState.getSnakesList()) {
            gameState.snakes.add(Snake.fromProtobuf(protoSnake));
        }

        for (SnakesProto.GameState.Coord protoFood : protoState.getFoodsList()) {
            gameState.foods.add(new Food(Coordinate.fromProtobuf(protoFood)));
        }

        for (SnakesProto.GamePlayer protoPlayer : protoState.getPlayers().getPlayersList()) {
            gameState.players.add(Player.fromProtobuf(protoPlayer));
        }

        return gameState;
    }

    public SnakesProto.GameState toProtobuf() {
        SnakesProto.GameState.Builder stateBuilder = SnakesProto.GameState.newBuilder();

        stateBuilder.setStateOrder(stateOrder);

        for (Snake snake : snakes) {
            stateBuilder.addSnakes(snake.toProtobuf());
        }

        for (Food food : foods) {
            stateBuilder.addFoods(food.getCoordinate().toProtobuf());
        }

        SnakesProto.GamePlayers.Builder playersBuilder = SnakesProto.GamePlayers.newBuilder();
        for (Player player : players) {
            playersBuilder.addPlayers(player.toProtobuf());
        }
        stateBuilder.setPlayers(playersBuilder);

        return stateBuilder.build();
    }

    public void addSnake(Snake snake) {
        snakes.add(snake);
    }

    public void removeSnake(Snake snake) {
        snakes.remove(snake);
    }

    public void addFood(Food food) {
        foods.add(food);
    }

//    public void removeFood(Coordinate food) {
//        foods.remove(food);
//    }

    public void incrementStateOrder() { stateOrder++; }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public void removeFood(Food food) { foods.remove(food); }

    public void clear() {
        snakes.clear();
        foods.clear();
        players.clear();
        stateOrder = 0;
    }

    public List<Snake> getSnakes() {
        return new ArrayList<>(snakes);
    }

    public List<Food> getFoods() {
        return new ArrayList<>(foods);
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    public int getStateOrder() {
        return stateOrder;
    }

    public void setStateOrder(int stateOrder) {
        this.stateOrder = stateOrder;
    }

    public int getFoodCount() {
        return foods.size();
    }

    public Snake getSnakeByPlayerId(int playerId) {
        return snakes.stream()
                .filter(snake -> snake.getPlayerId() == playerId)
                .findFirst()
                .orElse(null);
    }

    public Player getPlayer(int playerId) {
        return players.stream()
                .filter(player -> player.getId() == playerId)
                .findFirst()
                .orElse(null);
    }

    public SnakesProto.GamePlayers getPlayersProto() {
        var build = SnakesProto.GamePlayers.newBuilder();
        for(var player: players){
            build.addPlayers(player.toProtobuf());
        }

        return build.build();
    }
}