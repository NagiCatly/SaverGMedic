import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One location (the campus or a building interior): background, collision grid, doors, supply points. */
final class Scene {
    private static final int STEP = 4;                       // lattice used for the reachability search
    private static final int LATTICE = Config.WORLD / STEP;

    final SceneId id;
    final BufferedImage background;
    final double spawnX, spawnY;
    final double viewMin, viewMax;      // camera limits (the campus image has a black frame around it)
    final List<Portal> portals = new ArrayList<Portal>();
    final List<SupplyPoint> supplies = new ArrayList<SupplyPoint>();

    private final boolean[][] blocked = new boolean[Config.GRID][Config.GRID];   // [row][col]
    private boolean[][] reachable = new boolean[LATTICE][LATTICE];                  // [x][y]
    private final List<double[]> patientSpots = new ArrayList<double[]>();

    Scene(SceneId id, BufferedImage background, double spawnX, double spawnY) {
        this.id = id;
        this.background = background;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.viewMin = id == SceneId.CAMPUS ? 40 : 0;
        this.viewMax = id == SceneId.CAMPUS ? Config.WORLD - 40 : Config.WORLD;
        String[] rows = id.grid();
        if (rows.length != Config.GRID) throw new IllegalStateException("Bad map height for " + id);
        for (int r = 0; r < Config.GRID; r++) {
            if (rows[r].length() != Config.GRID) throw new IllegalStateException("Bad map width for " + id + " row " + r);
            for (int c = 0; c < Config.GRID; c++) blocked[r][c] = rows[r].charAt(c) == '#';
        }
    }

    void addPortal(Portal p) { portals.add(p); }

    void addSupply(SupplyPoint s) { supplies.add(s); }

    boolean isCellBlocked(int col, int row) {
        if (col < 0 || row < 0 || col >= Config.GRID || row >= Config.GRID) return true;
        return blocked[row][col];
    }

    /** True if the axis-aligned box touches any blocked cell (or leaves the map). */
    boolean boxBlocked(double left, double top, double right, double bottom) {
        int c1 = (int) Math.floor(left / Config.CELL);
        int c2 = (int) Math.floor((right - 1e-6) / Config.CELL);
        int r1 = (int) Math.floor(top / Config.CELL);
        int r2 = (int) Math.floor((bottom - 1e-6) / Config.CELL);
        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                if (isCellBlocked(c, r)) return true;
            }
        }
        return false;
    }

    /** Collision test for something whose feet are at (x, y). */
    boolean footBlocked(double x, double y) {
        return boxBlocked(x - Config.FOOT_HALF_W, y - Config.FOOT_H, x + Config.FOOT_HALF_W, y);
    }

    Portal portalAt(double x, double y) {
        for (Portal p : portals) {
            if (p.contains(x, y)) return p;
        }
        return null;
    }

    Portal portalTo(SceneId target) {
        for (Portal p : portals) {
            if (p.target == target) return p;
        }
        return null;
    }

    SupplyPoint supplyNear(double x, double y) {
        for (SupplyPoint s : supplies) {
            if (s.isNear(x, y)) return s;
        }
        return null;
    }

    /** Could the player really walk to this point from the spawn point? */
    boolean isReachable(double x, double y) {
        int ix = (int) Math.round(x / STEP), iy = (int) Math.round(y / STEP);
        if (ix < 0 || iy < 0 || ix >= LATTICE || iy >= LATTICE) return false;
        return reachable[ix][iy];
    }

    List<double[]> patientSpots() { return Collections.unmodifiableList(patientSpots); }

    /** Call once after every portal and supply point has been added. */
    void finish() {
        computeReachability();
        computePatientSpots();
    }

    private void computeReachability() {
        reachable = new boolean[LATTICE][LATTICE];
        int sx = (int) Math.round(spawnX / STEP), sy = (int) Math.round(spawnY / STEP);
        if (footBlocked(sx * STEP, sy * STEP)) {
            System.err.println("WARNING: spawn point is blocked in " + id);
            return;
        }
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        reachable[sx][sy] = true;
        queue.add(new int[] {sx, sy});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= LATTICE || ny >= LATTICE || reachable[nx][ny]) continue;
                if (footBlocked(nx * STEP, ny * STEP)) continue;
                reachable[nx][ny] = true;
                queue.add(new int[] {nx, ny});
            }
        }
    }

    private void computePatientSpots() {
        patientSpots.clear();
        for (int gx = 0; gx < LATTICE; gx += 4) {
            for (int gy = 0; gy < LATTICE; gy += 4) {
                if (!reachable[gx][gy]) continue;
                double x = gx * STEP, y = gy * STEP;
                if (boxBlocked(x - 34, y - 30, x + 34, y + 6)) continue;          // room for the lying sprite
                if (Math.hypot(x - spawnX, y - spawnY) < 100) continue;
                boolean nearDoor = false;
                for (Portal p : portals) {
                    if (x > p.x - 60 && x < p.x + p.w + 60 && y > p.y - 60 && y < p.y + p.h + 60) nearDoor = true;
                }
                for (SupplyPoint s : supplies) {
                    if (Math.hypot(x - s.x, y - s.y) < 70) nearDoor = true;
                }
                if (nearDoor) continue;
                patientSpots.add(new double[] {x, y});
            }
        }
    }
}
