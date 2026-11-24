import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** The medic controlled by the user. */
final class Player extends Entity {
    private PlayerSkin skin;
    private Direction facing = Direction.DOWN;
    private boolean moving;
    private double animClock;

    Player(PlayerSkin skin, double x, double y) {
        super(x, y);
        this.skin = skin;
    }

    void setSkin(PlayerSkin skin) { this.skin = skin; }

    Direction getFacing() { return facing; }

    boolean isMoving() { return moving; }

    /** Stops the walking animation (used while treating or changing rooms). */
    void halt() { moving = false; }

    /** Moves by one frame of input; collides against the scene one pixel at a time. */
    void move(double dt, double ix, double iy, boolean sprint, Scene scene) {
        moving = false;
        double len = Math.hypot(ix, iy);
        if (len <= 0) return;
        ix /= len;
        iy /= len;
        double speed = sprint ? Config.SPRINT_SPEED : Config.WALK_SPEED;
        double bx = x, by = y;
        moveAxis(ix * speed * dt, 0, scene);
        moveAxis(0, iy * speed * dt, scene);
        moving = Math.abs(x - bx) + Math.abs(y - by) > 1e-6;
        updateFacing(ix, iy);
    }

    private void updateFacing(double ix, double iy) {
        double ax = Math.abs(ix), ay = Math.abs(iy);
        boolean horizontal;
        if (ax > ay + 1e-9) {
            horizontal = true;
        } else if (ay > ax + 1e-9) {
            horizontal = false;
        } else {
            horizontal = facing == Direction.LEFT || facing == Direction.RIGHT;   // diagonal: keep the current axis
        }
        if (horizontal) {
            facing = ix > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            facing = iy > 0 ? Direction.DOWN : Direction.UP;
        }
    }

    private void moveAxis(double dx, double dy, Scene scene) {
        double remaining = Math.abs(dx) + Math.abs(dy);
        if (remaining <= 0) return;
        double sx = Math.signum(dx), sy = Math.signum(dy);
        while (remaining > 1e-9) {
            double step = Math.min(1.0, remaining);
            double nx = x + sx * step, ny = y + sy * step;
            if (scene.footBlocked(nx, ny)) break;
            x = nx;
            y = ny;
            remaining -= step;
        }
    }

    @Override
    void update(double dt) {
        if (moving) animClock += dt; else animClock = 0;
    }

    @Override
    void draw(Graphics2D g, double time) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(x - 11, y - 4, 22, 8));
        Sprite s = skin.forFacing(facing);
        BufferedImage frame = moving ? s.frameAt(animClock) : s.firstFrame();
        Sprite.draw(g, frame, x, y + 1, skin.scale, s.isSmooth(), 1f);
    }
}
