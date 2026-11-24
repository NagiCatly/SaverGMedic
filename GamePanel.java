import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The game screen: runs the loop, owns the state machine and draws the world. */
@SuppressWarnings("serial")
final class GamePanel extends JPanel {
    private final Map<SceneId, Scene> scenes;
    private final List<PlayerSkin> skins;
    private final Sprite injured, healed;
    private final Hud hud;
    private final KeyHandler keys = new KeyHandler();
    private final Timer timer;

    private GameState state = GameState.TITLE;
    private GameSession session;
    private int skinIndex;
    private int tutorialPage;
    private boolean tutorialMode;
    private boolean tutorialGuideOpen;
    private int narratorStep;
    private double tutorialStartX, tutorialStartY;
    private int bestScore;
    private boolean debug;
    private boolean snapCamera = true;

    private double camX, camY;
    private double clock;       // global animation clock (seconds)
    private double endTime;
    private long lastNanos;

    GamePanel(Map<SceneId, Scene> scenes, List<PlayerSkin> skins, BufferedImage kitIcon, Sprite injured, Sprite healed) {
        this.scenes = scenes;
        this.skins = skins;
        this.injured = injured;
        this.healed = healed;
        this.hud = new Hud(kitIcon);

        setPreferredSize(new Dimension(Config.VIEW_W, Config.VIEW_H));
        setBackground(Color.BLACK);
        setFocusable(true);
        setDoubleBuffered(true);
        addKeyListener(keys);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                keys.clear();
                if (state == GameState.PLAYING) state = GameState.PAUSED;   // pause when the window loses focus
            }
        });

        timer = new Timer(8, e -> tick());
        timer.setCoalesce(true);
    }

    void start() {
        lastNanos = System.nanoTime();
        timer.start();
    }

    // ------------------------------------------------------------------ loop

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        clock += dt;
        update(dt);
        repaint();
        java.awt.Toolkit.getDefaultToolkit().sync();
    }

    void update(double dt) {
        if (keys.consume(KeyEvent.VK_F3)) debug = !debug;

        switch (state) {
            case TITLE:
                if (keys.consumeAny(KeyEvent.VK_LEFT, KeyEvent.VK_A)) skinIndex = (skinIndex + skins.size() - 1) % skins.size();
                if (keys.consumeAny(KeyEvent.VK_RIGHT, KeyEvent.VK_D)) skinIndex = (skinIndex + 1) % skins.size();
                if (keys.consume(KeyEvent.VK_1)) { tutorialMode = true; startGame(); }
                if (keys.consume(KeyEvent.VK_2) || keys.consumeAny(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) startGame();
                break;
            case PLAYING:
                if (tutorialMode && keys.consume(KeyEvent.VK_G)) {
                    tutorialGuideOpen = !tutorialGuideOpen;
                    keys.clear();
                } else if (tutorialMode && tutorialGuideOpen) {
                    if (keys.consumeAny(KeyEvent.VK_ESCAPE, KeyEvent.VK_G)) tutorialGuideOpen = false;
                } else if (keys.consumeAny(KeyEvent.VK_ESCAPE, KeyEvent.VK_P)) {
                    state = GameState.PAUSED;
                } else {
                    if (!tutorialGuideOpen) session.update(dt, keys);
                    if (session.consumeSceneChanged()) snapCamera = true;
                    if (tutorialMode) updateTutorialNarrator();
                    if (tutorialMode && session.getLevelRescued() >= 1) {
                        tutorialMode = false;
                        state = GameState.TUTORIAL_DONE;
                        break;
                    }
                    updateCamera(dt);
                    if (session.getResult() != GameSession.Result.RUNNING) {
                        state = GameState.ENDED;
                        endTime = 0;
                        bestScore = Math.max(bestScore, session.getScore());
                    }
                }
                break;
            case TUTORIAL_DONE:
                if (keys.consumeAny(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) { tutorialMode = false; startGame(); }
                else if (keys.consumeAny(KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE)) { tutorialMode = false; state = GameState.TITLE; }
                break;
            case PAUSED:
                if (keys.consumeAny(KeyEvent.VK_ESCAPE, KeyEvent.VK_P, KeyEvent.VK_ENTER)) {
                    state = GameState.PLAYING;
                } else if (keys.consume(KeyEvent.VK_T)) {
                    state = GameState.TITLE;
                }
                break;
            case ENDED:
                endTime += dt;
                session.update(dt, keys);   // lets floating texts finish
                if (endTime > 0.6) {
                    if (keys.consume(KeyEvent.VK_R)) startGame();
                    else if (keys.consumeAny(KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE)) state = GameState.TITLE;
                }
                break;
            default:
                break;
        }
        keys.endFrame();
    }

    private void startGame() {
        session = new GameSession(scenes, skins.get(skinIndex), injured, healed);
        tutorialGuideOpen = false;
        narratorStep = 0;
        tutorialStartX = session.getPlayer().getX();
        tutorialStartY = session.getPlayer().getY();
        snapCamera = true;
        keys.clear();
        state = GameState.PLAYING;
        updateCamera(0);
    }

    private void updateTutorialNarrator() {
        if (!tutorialMode || session == null || tutorialGuideOpen) return;
        Player p = session.getPlayer();
        double moved = Math.hypot(p.getX() - tutorialStartX, p.getY() - tutorialStartY);
        if (narratorStep == 0 && moved > 55) narratorStep = 1;
        if (narratorStep == 1 && session.isDialogueOpen()) narratorStep = 2;
        Patient nearby = session.treatablePatient();
        if (narratorStep == 2 && !session.isDialogueOpen() && nearby != null && nearby.isReadyForTreatment()) narratorStep = 3;
        if (narratorStep == 3 && session.getLevelRescued() >= 1) narratorStep = 4;
    }

    private String narratorMessage() {
        switch (narratorStep) {
            case 0: return "Welcome, Medic! I am your narrator. First, move around using W, A, S, D or the ARROW KEYS.";
            case 1: return "Good job! Now follow the red arrow to find an injured patient. Walk close and press E to talk.";
            case 2: return "Listen to the patient carefully. Choose the safest response with 1, 2, or 3. Take your time!";
            case 3: return "Well done! The patient is ready for help. Stand beside them and press E again to use a medical kit.";
            default: return "Excellent rescue! You have completed the practice. Press ENTER to start the real game.";
        }
    }

    // ------------------------------------------------------------------ camera

    private double zoom() {
        double sx = (double) getWidth() / Config.VIEW_W, sy = (double) getHeight() / Config.VIEW_H;
        double s = Math.min(sx, sy);
        return Config.ZOOM * (s > 0 ? s : 1.0);
    }

    private static double clampCam(double cam, double viewSize, double min, double max) {
        if (viewSize >= max - min) return (min + max - viewSize) / 2.0;   // map smaller than window: centre it
        return Math.max(min, Math.min(max - viewSize, cam));
    }

    private void updateCamera(double dt) {
        if (session == null) return;
        double z = zoom();
        double viewW = getWidth() / z, viewH = getHeight() / z;
        if (getWidth() <= 0 || getHeight() <= 0) { viewW = Config.VIEW_W / Config.ZOOM; viewH = Config.VIEW_H / Config.ZOOM; }
        Player p = session.getPlayer();
        Scene sc = session.getScene();
        double tx = clampCam(p.getX() - viewW / 2.0, viewW, sc.viewMin, sc.viewMax);
        double ty = clampCam(p.getY() - 20 - viewH / 2.0, viewH, sc.viewMin, sc.viewMax);
        if (snapCamera) {
            camX = tx;
            camY = ty;
            snapCamera = false;
        } else {
            double k = 1 - Math.exp(-12 * dt);
            camX += (tx - camX) * k;
            camY += (ty - camY) * k;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            int w = getWidth(), h = getHeight();

            switch (state) {
                case TITLE:
                    hud.drawTitle(g, w, h, scenes.get(SceneId.CAMPUS).background, skins.get(skinIndex), clock);
                    break;
                case PLAYING:
                    if (session != null) drawWorld(g, w, h);
                    break;
                case TUTORIAL_DONE:
                    hud.drawTutorialComplete(g, w, h);
                    break;
                case PAUSED:
                case ENDED:
                    if (session != null) {
                        drawWorld(g, w, h);
                        if (state == GameState.PAUSED) hud.drawPause(g, w, h);
                        if (state == GameState.ENDED) hud.drawEnd(g, w, h, session, bestScore, endTime, clock);
                    }
                    break;
                default:
                    break;
            }
        } finally {
            g.dispose();
        }
    }

    private void drawWorld(Graphics2D g, int w, int h) {
        double z = zoom();
        double rx = Math.round(camX * z) / z;     // snap the camera to whole screen pixels: no shimmering
        double ry = Math.round(camY * z) / z;
        Scene sc = session.getScene();
        Player player = session.getPlayer();

        AffineTransform screen = g.getTransform();
        g.scale(z, z);
        g.translate(-rx, -ry);

        g.drawImage(sc.background, 0, 0, null);

        // supply point markers
        for (SupplyPoint sp : sc.supplies) drawSupplyMarker(g, sp);

        // everything standing in the scene, back to front
        List<Entity> drawList = new ArrayList<Entity>(session.patientsIn(sc));
        drawList.add(player);
        drawList.sort(Comparator.comparingDouble(Entity::sortKey));
        for (Entity e : drawList) e.draw(g, clock);

        for (FloatingText ft : session.floatsIn(sc)) ft.draw(g);

        if (debug) drawDebug(g, sc);

        g.setTransform(screen);

        // screen-space UI
        int psx = (int) Math.round((player.getX() - rx) * z);
        int psy = (int) Math.round((player.getY() - ry) * z);
        GameSession.Hint hint = session.hint();
        if (hint != null && !(hint.sameScene && player.distanceTo(hint.x, hint.y) < 110)) {
            double angle = Math.atan2(hint.y - (player.getY() - 20), hint.x - player.getX());
            hud.drawHint(g, psx, psy, angle, hint.label, hint.sameScene);
        }
        hud.drawPrompt(g, psx, psy, session);
        hud.drawPlaying(g, w, h, session, clock);
        if (tutorialMode) hud.drawNarrator(g, w, h, narratorMessage(), narratorStep + 1);
        hud.drawDialogue(g, w, h, session);
        if (tutorialMode && tutorialGuideOpen) hud.drawTutorialGuide(g, w, h, session);
    }

    private void drawSupplyMarker(Graphics2D g, SupplyPoint sp) {
        double bob = Math.sin(clock * 3.0) * 3;
        int cx = (int) Math.round(sp.x), cy = (int) Math.round(sp.y - 100 + bob);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval(cx - 11, cy - 11 + 2, 22, 22);
        g.setColor(new Color(235, 245, 235));
        g.fillOval(cx - 11, cy - 11, 22, 22);
        g.setColor(new Color(220, 40, 50));
        g.fillRect(cx - 7, cy - 2, 14, 4);
        g.fillRect(cx - 2, cy - 7, 4, 14);
        g.setColor(new Color(40, 40, 50));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(cx - 11, cy - 11, 22, 22);
    }

    private void drawDebug(Graphics2D g, Scene sc) {
        java.awt.Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
        g.setColor(Color.RED);
        for (int r = 0; r < Config.GRID; r++) {
            for (int c = 0; c < Config.GRID; c++) {
                if (sc.isCellBlocked(c, r)) g.fillRect(c * Config.CELL, r * Config.CELL, Config.CELL, Config.CELL);
            }
        }
        g.setColor(Color.YELLOW);
        for (double[] s : sc.patientSpots()) g.fillRect((int) s[0] - 1, (int) s[1] - 1, 3, 3);
        g.setComposite(old);
        g.setStroke(new BasicStroke(2f));
        g.setColor(Color.CYAN);
        for (Portal p : sc.portals) g.drawRect((int) p.x, (int) p.y, (int) p.w, (int) p.h);
        g.setColor(Color.GREEN);
        for (SupplyPoint s : sc.supplies) g.drawOval((int) (s.x - s.radius), (int) (s.y - s.radius), (int) (s.radius * 2), (int) (s.radius * 2));
        Player p = session.getPlayer();
        g.setColor(Color.WHITE);
        g.drawRect((int) (p.getX() - Config.FOOT_HALF_W), (int) (p.getY() - Config.FOOT_H), (int) (Config.FOOT_HALF_W * 2), (int) Config.FOOT_H);
    }
}
