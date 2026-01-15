package org.example.snake.entities;

import org.example.snake.protocol.SnakesProto;

public enum Direction {
    UP(0),
    DOWN(1),
    LEFT(2),
    RIGHT(3);

    private final int value;

    Direction(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public boolean isOpposite(Direction other) {
        return this == other.getOpposite();
    }

    public Direction getOpposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    public static Direction fromProtobuf(SnakesProto.Direction direction) {
        return switch (direction) {
            case UP -> UP;
            case DOWN -> DOWN;
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
        };
    }

    public SnakesProto.Direction toProtobuf() {
        return switch (this) {
            case UP -> SnakesProto.Direction.UP;
            case DOWN -> SnakesProto.Direction.DOWN;
            case LEFT -> SnakesProto.Direction.LEFT;
            case RIGHT -> SnakesProto.Direction.RIGHT;
        };
    }
}
