package org.example.snake.entities;

import org.example.snake.protocol.SnakesProto;

import java.util.ArrayList;
import java.util.List;

public class Snake {
    private int playerId;
    private final List<Coordinate> points;
    private SnakesProto.GameState.Snake.SnakeState state;
    private Direction headDirection;
    private Direction requestedDirection;
    private boolean dead = false;
    private boolean grow = false;

    public Snake(int playerId, List<Coordinate> points,
                 Direction headDirection,
                 SnakesProto.GameState.Snake.SnakeState state) {
        this.playerId = playerId;
        this.points = new ArrayList<>(points);
        this.headDirection = headDirection;
        this.requestedDirection = headDirection;
        this.state = state;
    }

    public static Snake createNewSnake(int playerId, Coordinate head) {
        List<Coordinate> points = new ArrayList<>();
        points.add(head);

        var direction = getRandomDirection();

        Direction tailDirection = direction.getOpposite();
        Coordinate tail = new Coordinate(0, 0).move(tailDirection);
        points.add(tail);

        return new Snake(playerId, points, direction,
                SnakesProto.GameState.Snake.SnakeState.ALIVE);
    }

    private static Direction getRandomDirection(){
        var r = Math.random();
        if (r < 0.25) return Direction.UP;
        if (r < 0.5) return Direction.DOWN;
        if (r < 0.75) return Direction.LEFT;
        return Direction.RIGHT;
    }

    public void eat(){
        grow = true;
    }

    public void zombie() {
        state = SnakesProto.GameState.Snake.SnakeState.ZOMBIE;
        playerId = -1;
    }

    public void moveHead(int fieldWidth, int fieldHeight) {
        if (requestedDirection != null && !headDirection.isOpposite(requestedDirection) && !headDirection.equals(requestedDirection)) {
            headDirection = requestedDirection;
            points.add(1, new Coordinate(0, 0).move(headDirection.getOpposite()));
        } else {
            points.get(1).absoluteInc();
        }

        points.get(0).move(headDirection, fieldWidth, fieldHeight);

    }

    public void moveTail() {
        if (!grow) {
            points.get(points.size() - 1).absoluteDec();
            if (points.get(points.size() - 1).equals(new Coordinate(0,0))){
                points.remove(points.size() - 1);
            }
        }
        grow = false;
    }

    public boolean collision(int fieldWidth, int fieldHeight) {
        var p1 = new Coordinate(points.get(0).getX(), points.get(0).getY());
        p1.move(headDirection.getOpposite(), fieldWidth, fieldHeight);
        for (var i = 1; i < points.size(); ++i){
            var direction = getDirectionByOffset(points.get(i));
            var p2 = Coordinate.add(p1, points.get(i), fieldWidth, fieldHeight);
            if (points.get(0).onLine(p1, p2, direction))
                return true;
            p1 = p2;
        }

        var d = getAllCellsAbsolute(fieldWidth, fieldHeight);
        d.remove(0);
        if (d.contains(getHeadAbsolute())) {
            return true;
        }

        return false;
    }

    private Direction getDirectionByOffset(Coordinate offset){
        if (offset.getX() == 0){
            if (offset.getY() > 0) return Direction.DOWN;
            else return Direction.UP;
        }
        if (offset.getY() == 0) {
            if (offset.getX() > 0) return Direction.RIGHT;
            else return Direction.LEFT;
        }
        return Direction.UP;
    }

    public boolean collision(Coordinate other, int fieldWidth, int fieldHeight) {
        var p1 = points.get(0);
        for (var i = 1; i < points.size(); ++i){
            var direction = getDirectionByOffset(points.get(i));
            var p2 = Coordinate.add(p1, points.get(i), fieldWidth, fieldHeight);
            if (other.onLine(p1, p2, direction))
                return true;
            p1 = p2;
        }

        return false;
    }

    public void requestDirection(Direction newDirection) {
        this.requestedDirection = newDirection;
    }

    public Coordinate getHeadAbsolute() {
        return points.get(0);
    }

    public List<Coordinate> getAllCellsAbsolute(int fieldWidth, int fieldHeight) {
        List<Coordinate> cells = new ArrayList<>();
        if (points.isEmpty()) return cells;

        var lastPoint = points.get(0);
        cells.add(points.get(0));
        var zero = new Coordinate(0, 0);
        for (var i = 1; i < points.size(); ++i){
            var curPoint = new Coordinate(points.get(i).getX(), points.get(i).getY());
            var nextPoint = Coordinate.add(lastPoint, curPoint, fieldWidth, fieldHeight);
            while (!curPoint.equals(zero)) {
                cells.add(Coordinate.add(lastPoint, curPoint, fieldWidth, fieldHeight));
                curPoint.absoluteDec();
            }
            lastPoint = nextPoint;
        }

        return cells;
    }

    public static Snake fromProtobuf(SnakesProto.GameState.Snake protoSnake) {
        List<Coordinate> points = new ArrayList<>();

        for (int i = 0; i < protoSnake.getPointsCount(); i++) {
            SnakesProto.GameState.Coord coord = protoSnake.getPoints(i);
            points.add(new Coordinate(coord.getX(), coord.getY()));
        }

        return new Snake(
                protoSnake.getPlayerId(),
                points,
                Direction.fromProtobuf(protoSnake.getHeadDirection()),
                protoSnake.getState()
        );
    }

    public SnakesProto.GameState.Snake toProtobuf() {
        SnakesProto.GameState.Snake.Builder builder =
                SnakesProto.GameState.Snake.newBuilder()
                        .setPlayerId(playerId)
                        .setState(state)
                        .setHeadDirection(headDirection.toProtobuf());

        for (Coordinate point : points) {
            builder.addPoints(point.toProtobuf());
        }

        return builder.build();
    }

    public int getPlayerId() { return playerId; }
    public List<Coordinate> getPoints() { return new ArrayList<>(points); }
    public SnakesProto.GameState.Snake.SnakeState getState() { return state; }
    public void setState(SnakesProto.GameState.Snake.SnakeState state) { this.state = state; }
    public Direction getHeadDirection() { return headDirection; }

    @Override
    public String toString() {
        return String.format("Snake[playerId=%d, points=%d, head=%s, state=%s]",
                playerId, points.size(), getHeadAbsolute(), state);
    }

    public boolean isDead() {
        return dead;
    }

    public void kill() { dead = true; }
}