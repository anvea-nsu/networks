package org.example.snake.roles;

import org.example.snake.protocol.SnakesProto;

public interface OnRoleChangeListener {
    void onRoleChangeRequest(NodeRole newRole);
    void leaveGame();
}
