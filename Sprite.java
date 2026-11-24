import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/** An animated (looping) set of frames, e.g. one GIF. */
final class Sprite {
    private final BufferedImage[] frames;
    private final int[] delays;
    private final int total;
    private final boolean smooth;

    Sprite(List<BufferedImage> frameList, List<Integer> delayList, boolean smooth) {
        if (frameList.isEmpty()) throw new IllegalArgumentException("Sprite needs at least one frame");
        this.frames = frameList.toArray(new BufferedImage[0]);
        this.delays = new int[frames.length];
        int sum = 0;
        for (int i = 0; i < delays.length; i++) {
            int d = i < delayList.size() ? delayList.get(i) : 120;
            delays[i] = Math.max(60, d);
            sum += delays[i];
        }
        this.total = sum;
        this.smooth = smooth;
    }

    BufferedImage firstFrame() { return frames[0]; }

    /** Frame to show after the given number of seconds (loops forever). */
    BufferedImage frameAt(double seconds) {
        if (frames.length == 1) return frames[0];
        long ms = (long) (seconds * 1000.0) % total;
        if (ms < 0) ms += total;
        for (int i = 0; i < frames.length; i++) {
            if (ms < delays[i]) return frames[i];
            ms -= delays[i];
        }
        return frames[frames.length - 1];
    }

    boolean isSmooth() { return smooth; }

    /** Draws img so that its bottom-centre sits on (x, feetY). */
    static void draw(Graphics2D g, BufferedImage img, double x, double feetY, double scale, boolean smooth, float alpha) {
        if (alpha <= 0f) return;
        int dw = (int) Math.round(img.getWidth() * scale);
        int dh = (int) Math.round(img.getHeight() * scale);
        int dx = (int) Math.round(x - dw / 2.0);
        int dy = (int) Math.round(feetY - dh);
        Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Composite oldComp = g.getComposite();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                smooth ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (alpha < 1f) g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        g.drawImage(img, dx, dy, dw, dh, null);
        g.setComposite(oldComp);
        if (oldHint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint);
    }
}
