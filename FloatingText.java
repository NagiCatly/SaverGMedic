import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Small text that rises and fades out (e.g. "+150"). */
final class FloatingText {
    private static final double LIFE = 1.3;
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);

    final SceneId scene;
    private final double x;
    private double y;
    private final String text;
    private final Color color;
    private double age;

    FloatingText(SceneId scene, double x, double y, String text, Color color) {
        this.scene = scene; this.x = x; this.y = y; this.text = text; this.color = color;
    }

    void update(double dt) {
        age += dt;
        y -= 26 * dt;
    }

    boolean isDone() { return age >= LIFE; }

    void draw(Graphics2D g) {
        float a = (float) Math.max(0, Math.min(1, 1 - (age / LIFE - 0.5) * 2));
        g.setFont(FONT);
        FontMetrics fm = g.getFontMetrics();
        int tx = (int) Math.round(x - fm.stringWidth(text) / 2.0);
        int ty = (int) Math.round(y);
        g.setColor(new Color(0, 0, 0, (int) (160 * a)));
        g.drawString(text, tx + 1, ty + 1);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (255 * a)));
        g.drawString(text, tx, ty);
    }
}
