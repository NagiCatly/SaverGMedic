/** A spot where the medic can press E to refill the first-aid kit bag. */
final class SupplyPoint {
    final double x, y, radius;
    final String label;

    SupplyPoint(double x, double y, double radius, String label) {
        this.x = x; this.y = y; this.radius = radius; this.label = label;
    }

    boolean isNear(double px, double py) {
        return Math.hypot(px - x, py - y) <= radius;
    }
}
