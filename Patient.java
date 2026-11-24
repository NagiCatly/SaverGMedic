import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** An injured student waiting for the medic. */
final class Patient extends Entity {
    enum State { WAITING, HEALED, LOST }

    private static final double SCALE = 0.5;
    private static final Font BANG_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);

    private final SceneId sceneId;
    private final int scenarioNumber;
    private final double maxPatience;
    private final Sprite injured;
    private final Sprite healed;
    private final double phase;

    private State state = State.WAITING;
    private double patience;
    private double treat;                 // 0..1 treatment progress
    private boolean treatedThisFrame;
    private boolean lostEvent;
    private boolean readyForTreatment;
    private double stateTime;

    Patient(SceneId sceneId, double x, double y, double patience, Sprite injured, Sprite healed, double phase, int scenarioNumber) {
        super(x, y);
        this.sceneId = sceneId;
        this.scenarioNumber = scenarioNumber;
        this.maxPatience = patience;
        this.patience = patience;
        this.injured = injured;
        this.healed = healed;
        this.phase = phase;
    }

    SceneId getSceneId() { return sceneId; }
    int getScenarioNumber() { return scenarioNumber; }
    boolean isWaiting() { return state == State.WAITING; }
    double urgency() { return Math.max(0, Math.min(1, patience / maxPatience)); }
    double getPatience() { return patience; }

    /** Adds treatment progress; returns true once the patient is fully treated. */
    boolean applyTreatment(double amount) {
        treatedThisFrame = true;
        treat = Math.min(1.0, treat + amount);
        return treat >= 1.0;
    }

    boolean isReadyForTreatment() { return readyForTreatment; }

    void markReadyForTreatment() { readyForTreatment = true; }

    void markHealed() {
        state = State.HEALED;
        stateTime = 0;
        treat = 1;
    }

    /** True exactly once after the patient ran out of patience. */
    boolean pollLost() {
        boolean r = lostEvent;
        lostEvent = false;
        return r;
    }

    boolean isFinished() {
        return (state == State.HEALED && stateTime >= 3.0) || (state == State.LOST && stateTime >= 1.0);
    }

    @Override
    void update(double dt) {
        stateTime += dt;
        if (state != State.WAITING) return;
        patience -= dt;
        if (!treatedThisFrame && treat > 0) treat = Math.max(0, treat - dt * 1.5);
        treatedThisFrame = false;
        if (patience <= 0) {
            patience = 0;
            state = State.LOST;
            stateTime = 0;
            lostEvent = true;
        }
    }

    @Override
    void draw(Graphics2D g, double time) {
        float alpha = 1f;
        if (state == State.HEALED && stateTime > 2.3) alpha = (float) Math.max(0, 1 - (stateTime - 2.3) / 0.7);
        if (state == State.LOST) alpha = (float) Math.max(0, 1 - stateTime / 1.0);

        g.setColor(new Color(0, 0, 0, (int) (60 * alpha)));
        g.fill(new Ellipse2D.Double(x - 20, y - 5, 40, 10));

        BufferedImage frame = state == State.HEALED ? healed.frameAt(stateTime) : injured.frameAt(time + phase);
        Sprite s = state == State.HEALED ? healed : injured;
        Sprite.draw(g, frame, x, y + 2, SCALE, s.isSmooth(), alpha);

        if (state != State.WAITING) return;

        double spriteH = frame.getHeight() * SCALE;
        int barW = 38, barH = 5;
        int bx = (int) Math.round(x - barW / 2.0);
        int by = (int) Math.round(y - spriteH - 14);
        double u = urgency();
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(bx - 1, by - 1, barW + 2, barH + 2, 4, 4);
        Color c = u > 0.5 ? new Color(70, 200, 90) : (u > 0.25 ? new Color(240, 190, 40) : new Color(230, 60, 60));
        g.setColor(c);
        g.fillRoundRect(bx, by, (int) Math.round(barW * u), barH, 3, 3);

        if (treat > 0.001) {
            int ty = (int) Math.round(y + 6);
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRoundRect(bx - 1, ty - 1, barW + 2, barH + 2, 4, 4);
            g.setColor(new Color(80, 170, 255));
            g.fillRoundRect(bx, ty, (int) Math.round(barW * treat), barH, 3, 3);
        }

        if (u < 0.3 && ((int) (time * 4)) % 2 == 0) {
            g.setFont(BANG_FONT);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(230, 40, 40));
            g.drawString("!", (int) Math.round(x - fm.stringWidth("!") / 2.0), by - 6);
        }
    }
}
