package org.example.snake.entities;

public class Food {
    private boolean eaten = false;
    private Coordinate coordinate;

    public Food(Coordinate coordinate){
        this.coordinate = coordinate;
    }

    public void eat() { eaten = true; }

    public boolean isEaten() {
        return eaten;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }
}
