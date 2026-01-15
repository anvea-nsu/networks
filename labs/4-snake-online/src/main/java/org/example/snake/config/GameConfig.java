package org.example.snake.config;

import org.example.snake.protocol.SnakesProto;

public class GameConfig {
    private final int width;
    private final int height;
    private final int foodStatic;
    private final int stateDelayMs;

    public GameConfig(int width, int height, int foodStatic, int stateDelayMs) {
        this.width = width;
        this.height = height;
        this.foodStatic = foodStatic;
        this.stateDelayMs = stateDelayMs;
    }

    public static GameConfig getDefault() {
        return new GameConfig(40, 30, 1, 1000);
    }

    public static GameConfig fromProtobuf(SnakesProto.GameConfig protoConfig) {
        return new GameConfig(
                protoConfig.getWidth(),
                protoConfig.getHeight(),
                protoConfig.getFoodStatic(),
                protoConfig.getStateDelayMs()
        );
    }

    public SnakesProto.GameConfig toProtobuf() {
        SnakesProto.GameConfig.Builder builder = SnakesProto.GameConfig.newBuilder();

        if (width != 0) builder.setWidth(width);
        if (height != 0) builder.setHeight(height);
        if (foodStatic != 0) builder.setFoodStatic(foodStatic);
        if (stateDelayMs != 0) builder.setStateDelayMs(stateDelayMs);

        return builder.build();
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getFoodStatic() { return foodStatic; }
    public int getStateDelayMs() { return stateDelayMs; }

    public int calculateRequiredFood(int aliveSnakesCount) {
        return foodStatic + aliveSnakesCount;
    }

    @Override
    public String toString() {
        return String.format(
                "GameConfig[width=%d, height=%d, food_static=%d, state_delay_ms=%d]",
                width, height, foodStatic, stateDelayMs
        );
    }

    public static class Builder {
        private int width = 40;
        private int height = 30;
        private int foodStatic = 1;
        private int stateDelayMs = 1000;

        public Builder setWidth(int width) {
            this.width = width;
            return this;
        }

        public Builder setHeight(int height) {
            this.height = height;
            return this;
        }

        public Builder setFoodStatic(int foodStatic) {
            this.foodStatic = foodStatic;
            return this;
        }

        public Builder setStateDelayMs(int stateDelayMs) {
            this.stateDelayMs = stateDelayMs;
            return this;
        }

        public GameConfig build() {
            return new GameConfig(width, height, foodStatic, stateDelayMs);
        }
    }
}