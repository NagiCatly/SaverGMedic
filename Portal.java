/** A door: walking into its rectangle moves the player to another scene. */
final class Portal {
    final double x, y, w, h;
    final SceneId target;
    final double spawnX, spawnY;
    final String label;

    Portal(double x, double y, double w, double h, SceneId target, double spawnX, double spawnY, String label) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.target = target;
        this.spawnX = spawnX; this.spawnY = spawnY;
        this.label = label;
    }

    boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    double centerX() { return x + w / 2; }
    double centerY() { return y + h / 2; }
}
