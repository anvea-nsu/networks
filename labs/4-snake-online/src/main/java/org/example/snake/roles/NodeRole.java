package org.example.snake.roles;

import org.example.snake.protocol.SnakesProto;

public enum NodeRole {
    NORMAL(0),
    VIEWER(3),
    DEPUTY(2),
    MASTER(1),
    MENU(4);

    private final int value;

    NodeRole(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static NodeRole fromProtobuf(SnakesProto.NodeRole role) {
        return switch (role) {
            case NORMAL -> NORMAL;
            case DEPUTY -> DEPUTY;
            case VIEWER -> VIEWER;
            case MASTER -> MASTER;
        };
    }

    public SnakesProto.NodeRole toProtobuf() {
        return switch (this) {
            case NORMAL -> SnakesProto.NodeRole.NORMAL;
            case DEPUTY -> SnakesProto.NodeRole.DEPUTY;
            case VIEWER -> SnakesProto.NodeRole.VIEWER;
            case MASTER -> SnakesProto.NodeRole.MASTER;
            case MENU -> throw new RuntimeException();
        };
    }
}
