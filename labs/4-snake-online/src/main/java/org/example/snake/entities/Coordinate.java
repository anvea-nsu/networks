package org.example.snake.entities;

import org.example.snake.protocol.SnakesProto;

/**
 * Координата на игровом поле (тор)
 */
public class Coordinate {
    private int x;
    private int y;

    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void move(Direction direction, int width, int height) {
        switch (direction) {
            case UP: y = (y - 1 + height) % height; break;
            case DOWN: y = (y + 1) % height; break;
            case LEFT: x = (x - 1 + width) % width; break;
            case RIGHT: x = (x + 1) % width;
        };
    }

    public Coordinate move(Direction direction) {
        return switch (direction) {
            case UP -> new Coordinate(x, y - 1);
            case DOWN -> new Coordinate(x, y + 1);
            case LEFT -> new Coordinate(x - 1, y);
            case RIGHT -> new Coordinate(x + 1, y);
        };
    }

    public boolean onLine(Coordinate p1, Coordinate p2, Direction direction){
        if (p1.x == p2.x && p1.x == x){
            if (p1.y <= p2.y && direction == Direction.DOWN && y >= p1.y && y <= p2.y)
                return true;
            if (p1.y <= p2.y && direction == Direction.UP && (y <= p1.y || y >= p2.y))
                return true;
            if (p1.y > p2.y && direction == Direction.DOWN && (y <= p2.y || y >= p1.y))
                return true;
            if (p1.y > p2.y && direction == Direction.UP && y <= p1.y && y >= p2.y)
                return true;
        }

        if (p1.y == p2.y && p1.y == y){
            if (p1.x <= p2.x && direction == Direction.RIGHT && x >= p1.x && x <= p2.x)
                return true;
            if (p1.x <= p2.x && direction == Direction.LEFT && (x <= p1.x || x >= p2.x))
                return true;
            if (p1.x > p2.x && direction == Direction.RIGHT && (x <= p2.x || x >= p1.x))
                return true;
            if (p1.x > p2.x && direction == Direction.LEFT && x <= p1.x && x >= p2.x)
                return true;
        }

        return false;
    }

    public static Coordinate add(Coordinate p1, Coordinate p2, int width, int height){
        var newX = (p1.x + p2.x + width) % width;
        var newY = (p1.y + p2.y + height) % height;
        return new Coordinate(newX, newY);
    }

    public void absoluteInc(){
        if (x == 0){
            y += y < 0 ? - 1 : 1;
        } else if (y == 0) {
            x += x < 0 ? - 1 : 1;
        }
    }

    public void absoluteDec(){
        if (x == 0){
            y += y < 0 ? 1 : -1;
        } else if (y == 0) {
            x += x < 0 ? 1 : -1;
        }
    }

    public static Coordinate fromProtobuf(SnakesProto.GameState.Coord coord) {
        return new Coordinate(coord.getX(), coord.getY());
    }

    public SnakesProto.GameState.Coord toProtobuf() {
        return SnakesProto.GameState.Coord.newBuilder()
                .setX(x)
                .setY(y)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinate that = (Coordinate) o;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}