import java.awt.Graphics2D;

/** Anything that stands somewhere in the world. (x, y) is the point where its feet touch the ground. */
abstract class Entity {
    protected double x;
    protected double y;

    protected Entity(double x, double y) {
        this.x = x;
        this.y = y;
    }

    double getX() { return x; }
    double getY() { return y; }

    void setPosition(double nx, double ny) {
        this.x = nx;
        this.y = ny;
    }

    double distanceTo(double px, double py) {
        return Math.hypot(px - x, py - y);
    }

    /** Entities with a larger sort key are drawn later (in front). */
    double sortKey() { return y; }

    abstract void update(double dt);

    abstract void draw(Graphics2D g, double time);
}
