/** Central place for every tunable number in the game. */
final class Config {
    private Config() {}

    // World / rendering
    static final int WORLD = 1024;          // every map image is 1024 x 1024
    static final int CELL = 16;             // collision cell size in pixels
    static final int GRID = WORLD / CELL;   // 64 x 64 collision cells
    static final int VIEW_W = 960;          // window content size
    static final int VIEW_H = 640;
    static final double ZOOM = 1.5;         // world -> screen scale

    // Player feet box (used for collisions)
    static final double FOOT_HALF_W = 6;
    static final double FOOT_H = 8;
    static final double WALK_SPEED = 150;   // world pixels / second
    static final double SPRINT_SPEED = 235;

    // Rules
    static final int MAX_KITS = 3;
    static final int START_LIVES = 3;
    static final int QUOTA = 12;              // patients to treat to win
    static final double SHIFT_SECONDS = 480;  // 8 minutes
    static final double TREAT_SECONDS = 1.4;  // hold E this long to treat
    static final double TREAT_RANGE = 46;
    static final double SUPPLY_RANGE = 60;
    static final double PATIENCE_CAMPUS = 95;
    static final double PATIENCE_INDOOR = 110;
}
