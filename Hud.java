import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Draws every piece of 2D user interface (screen space). */
final class Hud {
    private static final Font BIG = new Font(Font.SANS_SERIF, Font.BOLD, 40);
    private static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 54);
    private static final Font MED = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    private static final Font NORMAL = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    private static final Font BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

    private static final Color PANEL = new Color(10, 20, 35, 170);
    private static final Color WHITE = new Color(245, 248, 255);
    private static final Color GOLD = new Color(255, 214, 90);

    private final BufferedImage kitIcon;

    Hud(BufferedImage kitIcon) {
        this.kitIcon = kitIcon;
    }

    // ------------------------------------------------------------------ in-game

    void drawPlaying(Graphics2D g, int w, int h, GameSession s, double clock) {
        // top bar
        g.setColor(PANEL);
        g.fillRect(0, 0, w, 48);

        // kits
        for (int i = 0; i < Config.MAX_KITS; i++) {
            float a = i < s.getKits() ? 1f : 0.25f;
            drawIcon(g, kitIcon, 14 + i * 34, 9, 30, a);
        }
        // lives
        for (int i = 0; i < Config.START_LIVES; i++) {
            heart(g, 150 + i * 30, 24, 10, i < s.getLives() ? new Color(235, 60, 80) : new Color(90, 60, 70));
        }
        // quota
        g.setFont(BOLD);
        shadowText(g, "Lv " + s.getLevel() + "   Rescued " + s.getLevelRescued() + " / " + s.getLevelTarget(), 250, 30, WHITE);

        // timer
        int secs = (int) Math.ceil(s.getTimeLeft());
        String time = String.format("%d:%02d", secs / 60, secs % 60);
        boolean urgent = secs <= 30 && ((int) (clock * 2)) % 2 == 0;
        g.setFont(MED);
        centerText(g, time, w / 2, 32, urgent ? new Color(255, 90, 90) : WHITE);

        // score
        g.setFont(MED);
        String score = "Score " + s.getScore();
        shadowText(g, score, w - 14 - g.getFontMetrics().stringWidth(score), 32, GOLD);

        // location
        g.setFont(SMALL);
        centerText(g, s.getScene().id.title, w / 2, 66, new Color(255, 255, 255, 200));

        // waiting patients box
        drawWaiting(g, h, s);

        // Instructions stay at the top; tutorial details open with G.
        g.setFont(SMALL);
        String help = "WASD / Arrows: Move   Shift: Run   E: Interact   P: Pause";
        g.setColor(new Color(5, 15, 28, 190));
        g.fillRoundRect(14, 78, Math.min(w - 28, 560), 25, 10, 10);
        shadowText(g, help, 24, 95, new Color(255, 255, 255, 225));

        // toast
        if (s.getToastTime() > 0 && !s.getToast().isEmpty()) {
            float a = (float) Math.min(1.0, s.getToastTime() / 0.5);
            g.setFont(BOLD);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(s.getToast()) + 30;
            int tx = (w - tw) / 2, ty = h - 96;
            g.setColor(new Color(10, 20, 35, (int) (190 * a)));
            g.fillRoundRect(tx, ty, tw, 34, 16, 16);
            g.setColor(new Color(255, 255, 255, (int) (255 * a)));
            g.drawString(s.getToast(), tx + 15, ty + 23);
        }

        // Level-complete celebration overlay. Responsive sizing keeps the full
        // CONGRATULATIONS message inside the panel at different window sizes.
        if (s.getLevelCongratsTime() > 0) {
            int panelW = Math.min(w - 36, 1040);
            int panelH = 190;
            int panelX = (w - panelW) / 2;
            int panelY = (h - panelH) / 2;
            g.setColor(new Color(4, 15, 30, 220));
            g.fillRoundRect(panelX, panelY, panelW, panelH, 28, 28);
            g.setStroke(new BasicStroke(4f));
            g.setColor(GOLD);
            g.drawRoundRect(panelX, panelY, panelW, panelH, 28, 28);

            String congrats = s.getLevelCongratsText();
            int maxTextWidth = panelW - 52;
            int fontSize = 54;
            Font congratsFont = TITLE.deriveFont((float) fontSize);
            g.setFont(congratsFont);
            while (g.getFontMetrics().stringWidth(congrats) > maxTextWidth && fontSize > 24) {
                fontSize -= 2;
                congratsFont = TITLE.deriveFont((float) fontSize);
                g.setFont(congratsFont);
            }
            centerText(g, congrats, w / 2, panelY + 65, GOLD);
            g.setFont(MED);
            centerText(g, "+1:30 TIME BONUS!", w / 2, panelY + 112, WHITE);
            g.setFont(SMALL);
            centerText(g, "Get ready for the next rescue level...", w / 2, panelY + 150, WHITE);
        }

        // hit flash
        if (s.getHitFlash() > 0) {
            g.setColor(new Color(220, 30, 30, (int) (110 * Math.min(1, s.getHitFlash() / 0.6))));
            g.fillRect(0, 0, w, h);
        }
        // door fade
        double fade = s.fadeAlpha();
        if (fade > 0) {
            g.setColor(new Color(0, 0, 0, (int) Math.round(255 * fade)));
            g.fillRect(0, 0, w, h);
        }
    }

    private void drawWaiting(Graphics2D g, int h, GameSession s) {
        Map<SceneId, Integer> counts = s.waitingCounts();
        StringBuilder sb = new StringBuilder("Waiting: ");
        if (counts.isEmpty()) {
            sb.append("none");
        } else {
            boolean first = true;
            for (Map.Entry<SceneId, Integer> e : counts.entrySet()) {
                if (!first) sb.append("  |  ");
                sb.append(e.getKey().title).append(' ').append(e.getValue());
                first = false;
            }
        }
        g.setFont(SMALL);
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(sb.toString()) + 24;
        g.setColor(PANEL);
        g.fillRoundRect(12, h - 44, bw, 30, 14, 14);
        g.setColor(WHITE);
        g.drawString(sb.toString(), 24, h - 24);
    }

    /** Prompt shown above the medic: what E will do right now. */
    void drawPrompt(Graphics2D g, int sx, int sy, GameSession s) {
        String text = null;
        if (s.treatablePatient() != null) {
            text = s.getKits() <= 0 ? "No kits - restock first!" : (s.treatablePatient().isReadyForTreatment() ? "Press [E] to treat patient" : "Press [E] to talk to patient");
        } else if (s.nearSupply() && s.getKits() < Config.MAX_KITS) {
            text = "Press [E] to restock kits";
        }
        if (text == null) return;
        g.setFont(BOLD);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text) + 20;
        g.setColor(new Color(10, 20, 35, 200));
        g.fillRoundRect(sx - tw / 2, sy - 100, tw, 26, 12, 12);
        g.setColor(WHITE);
        g.drawString(text, sx - tw / 2 + 10, sy - 82);
    }

    /** Arrow orbiting the medic and pointing at the nearest patient (or the door leading to one). */
    void drawHint(Graphics2D g, int sx, int sy, double angle, String label, boolean sameScene) {
        AffineTransform old = g.getTransform();
        g.translate(sx, sy - 24);
        g.rotate(angle);
        g.translate(78, 0);
        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(14, 0);
        arrow.lineTo(-8, -10);
        arrow.lineTo(-3, 0);
        arrow.lineTo(-8, 10);
        arrow.closePath();
        g.setColor(new Color(0, 0, 0, 120));
        g.translate(1.5, 1.5);
        g.fill(arrow);
        g.translate(-1.5, -1.5);
        g.setColor(sameScene ? new Color(255, 80, 80) : new Color(255, 200, 60));
        g.fill(arrow);
        g.setTransform(old);
        if (!sameScene) {
            g.setFont(SMALL);
            centerText(g, label, sx, sy + 38, new Color(255, 220, 90));
        }
    }


    /** Pokémon-style dialogue box with numbered decision choices. */
    void drawDialogue(Graphics2D g, int w, int h, GameSession s) {
        if (!s.isDialogueOpen()) return;
        int boxX = 34, boxY = h - 238, boxW = w - 68, boxH = 204;
        g.setColor(new Color(8, 15, 28, 238));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 22, 22);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(235, 235, 245));
        g.drawRoundRect(boxX, boxY, boxW, boxH, 22, 22);

        g.setFont(BOLD);
        shadowText(g, s.dialogueSpeaker(), boxX + 22, boxY + 30,
                GOLD);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        if (!s.dialogueFeedback().isEmpty()) {
            shadowText(g, s.dialogueFeedback(), boxX + 22, boxY + 55,
                    new Color(140, 255, 165));
        }
        g.setFont(NORMAL);
        shadowText(g, s.dialogueLine(), boxX + 22, boxY + 79, WHITE);

        String[] choices = s.dialogueChoices();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int i = 0; i < choices.length; i++) {
            int y = boxY + 112 + i * 25;
            shadowText(g, (i + 1) + ". " + choices[i], boxX + 24, y, WHITE);
        }
        g.setFont(SMALL);
        shadowText(g, "Press 1, 2, or 3 to choose", boxX + 22, boxY + boxH - 10, GOLD);
    }

    // ------------------------------------------------------------------ screens

    void drawTitle(Graphics2D g, int w, int h, BufferedImage background, PlayerSkin skin, double clock) {
        // blurred-ish backdrop: the campus map, darkened
        double scale = Math.max((double) w / background.getWidth(), (double) h / background.getHeight());
        int bw = (int) Math.round(background.getWidth() * scale), bh = (int) Math.round(background.getHeight() * scale);
        g.drawImage(background, (w - bw) / 2, (h - bh) / 2, bw, bh, null);
        g.setPaint(new GradientPaint(0, 0, new Color(5, 15, 30, 200), 0, h, new Color(5, 15, 30, 235)));
        g.fillRect(0, 0, w, h);

        g.setFont(TITLE);
        centerText(g, "CAMPUS MEDIC", w / 2, 108, WHITE);
        g.setFont(MED);
        centerText(g, "NSTP First-Aid Rescue", w / 2, 144, GOLD);

        // character card
        int cardW = 300, cardH = 220, cx = w / 2, cy = 175;
        g.setColor(PANEL);
        g.fillRoundRect(cx - cardW / 2, cy, cardW, cardH, 22, 22);
        Sprite s = skin.down;
        Sprite.draw(g, s.frameAt(clock), cx, cy + 165, skin.scale * 2.6, s.isSmooth(), 1f);
        g.setFont(MED);
        centerText(g, skin.name, cx, cy + 200, WHITE);
        g.setFont(BIG);
        centerText(g, "<", cx - cardW / 2 - 30, cy + cardH / 2 + 12, GOLD);
        centerText(g, ">", cx + cardW / 2 + 30, cy + cardH / 2 + 12, GOLD);
        g.setFont(SMALL);
        centerText(g, "Left / Right (or A / D) to choose your medic", cx, cy + cardH + 22, new Color(255, 255, 255, 200));

        g.setFont(NORMAL);
        int y = 440;
        String[] lines = {
            "Complete 10 rescue levels. Patient counts increase from 3 to 20.",
            "Walk to a patient and press [E] to talk. Choose safely with keys 1, 2, or 3, then press [E] again to treat.",
            "Restock kits at the medic tent and at first-aid boxes inside the buildings with [E].",
            "Patients inside the buildings are waiting too - follow the arrow! Lose all " + Config.START_LIVES + " lives and the shift is over."
        };
        for (String line : lines) {
            centerText(g, line, w / 2, y, new Color(255, 255, 255, 225));
            y += 26;
        }
        if (((int) (clock * 1.6)) % 2 == 0) {
            g.setFont(MED);
            centerText(g, "1: Tutorial Mode       2 / ENTER: Start Real Game", w / 2, h - 36, GOLD);
        }
    }


    void drawTutorial(Graphics2D g, int w, int h, int page, BufferedImage background) {
        double scale = Math.max((double) w / background.getWidth(), (double) h / background.getHeight());
        int bw = (int) Math.round(background.getWidth() * scale), bh = (int) Math.round(background.getHeight() * scale);
        g.drawImage(background, (w - bw) / 2, (h - bh) / 2, bw, bh, null);
        g.setColor(new Color(3, 12, 25, 220)); g.fillRect(0, 0, w, h);
        String[] titles = {"TUTORIAL 1: MOVEMENT", "TUTORIAL 2: FIND PATIENTS", "TUTORIAL 3: MAKE DECISIONS", "TUTORIAL 4: COMPLETE THE RESCUE"};
        String[][] lines = {
            {"Use W A S D or the ARROW KEYS to move.", "Hold SHIFT to run.", "Explore the campus and follow the on-screen guidance."},
            {"Walk close to an injured patient.", "Press E to begin the conversation.", "Patients may be waiting in different campus buildings."},
            {"Read the patient's situation carefully.", "Choose an answer with keys 1, 2, or 3.", "Good decisions earn points. Unsafe choices cost points and may use a life."},
            {"After completing the questions, approach the patient and press E to treat.", "Restock medical kits at the medic tent or first-aid points.", "Rescue everyone to clear the level. Each completed level adds time.", "Press ENTER on this page to begin the real game!"}
        };
        g.setFont(TITLE); centerText(g, titles[page], w / 2, 125, GOLD);
        g.setFont(MED); int y = 220;
        for (String line : lines[page]) { centerText(g, line, w / 2, y, WHITE); y += 48; }
        g.setFont(NORMAL); centerText(g, "LEFT: previous     ENTER / RIGHT: next     ESC: main menu", w / 2, h - 45, new Color(220,230,245));
        g.setFont(SMALL); centerText(g, "Page " + (page + 1) + " of 4", w / 2, h - 20, GOLD);
    }


    /** Step-by-step narrator panel shown during the playable tutorial. */
    void drawNarrator(Graphics2D g, int w, int h, String message, int step) {
        int boxX = 18, boxY = 112, boxW = Math.min(w - 36, 760), boxH = 76;
        g.setColor(new Color(4, 18, 34, 225));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 16, 16);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 214, 90));
        g.drawRoundRect(boxX, boxY, boxW, boxH, 16, 16);
        g.setFont(BOLD);
        shadowText(g, "NARRATOR  |  STEP " + Math.min(step, 5) + " OF 5", boxX + 16, boxY + 23, GOLD);
        g.setFont(NORMAL);
        FontMetrics fm = g.getFontMetrics();
        String remaining = message;
        int y = boxY + 49;
        while (!remaining.isEmpty() && y < boxY + boxH - 7) {
            int fit = 0;
            while (fit < remaining.length() && fm.stringWidth(remaining.substring(0, fit + 1)) <= boxW - 32) fit++;
            if (fit == 0) break;
            if (fit < remaining.length()) {
                int space = remaining.lastIndexOf(' ', fit);
                if (space > 0) fit = space;
            }
            shadowText(g, remaining.substring(0, fit), boxX + 16, y, WHITE);
            remaining = remaining.substring(fit).trim();
            y += 19;
        }
    }

    void drawTutorialGuide(Graphics2D g, int w, int h, GameSession s) {
        int boxW = Math.min(650, w - 60), boxH = 230;
        int x = (w - boxW) / 2, y = 82;
        g.setColor(new Color(0, 0, 0, 145));
        g.fillRoundRect(x - 8, y - 8, boxW + 16, boxH + 16, 24, 24);
        g.setColor(new Color(7, 24, 43, 245));
        g.fillRoundRect(x, y, boxW, boxH, 20, 20);
        g.setStroke(new BasicStroke(3f));
        g.setColor(GOLD);
        g.drawRoundRect(x, y, boxW, boxH, 20, 20);
        g.setFont(TITLE);
        centerText(g, "TUTORIAL GUIDE", w / 2, y + 43, GOLD);
        g.setFont(NORMAL);
        String[] lines = {
            "WASD / Arrow Keys  -  Move around the campus",
            "SHIFT  -  Run     |     E  -  Talk, treat, or restock kits",
            "1, 2, or 3  -  Choose an answer during patient dialogue",
            "Follow the arrow to locate the next injured patient.",
            "Answer every question correctly, then approach and press E to treat.",
            "G  -  Close this guide     |     P / ESC  -  Pause the game"
        };
        int yy = y + 82;
        for (String line : lines) {
            centerText(g, line, w / 2, yy, WHITE);
            yy += 25;
        }
    }

    void drawTutorialComplete(Graphics2D g, int w, int h) {
        g.setColor(new Color(4, 15, 30)); g.fillRect(0, 0, w, h);
        g.setFont(TITLE); centerText(g, "TUTORIAL COMPLETE!", w / 2, h / 2 - 65, GOLD);
        g.setFont(MED); centerText(g, "You moved, found a patient, answered the questions, and treated them.", w / 2, h / 2 - 12, WHITE);
        g.setFont(NORMAL); centerText(g, "ENTER: Start the real 10-level game     ESC: Main menu", w / 2, h / 2 + 48, new Color(220,230,245));
    }

    void drawPause(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, w, h);
        g.setFont(BIG);
        centerText(g, "PAUSED", w / 2, h / 2 - 10, WHITE);
        g.setFont(NORMAL);
        centerText(g, "P / Esc / Enter: resume      T: back to title", w / 2, h / 2 + 30, new Color(255, 255, 255, 220));
    }

    void drawEnd(Graphics2D g, int w, int h, GameSession s, int best, double endTime, double clock) {
        float a = (float) Math.min(1.0, endTime / 0.6);
        g.setColor(new Color(0, 0, 0, (int) (170 * a)));
        g.fillRect(0, 0, w, h);
        if (a < 1f) return;
        boolean won = s.getResult() == GameSession.Result.WON;
        g.setFont(TITLE);
        centerText(g, won ? "SHIFT COMPLETE!" : "SHIFT FAILED", w / 2, h / 2 - 90, won ? new Color(120, 255, 150) : new Color(255, 110, 110));
        g.setFont(NORMAL);
        String reason;
        if (won) reason = "Great work, medic - every patient is on the road to recovery.";
        else if (s.getResult() == GameSession.Result.LOST_LIVES) reason = "Too many patients were left waiting too long.";
        else reason = "Time ran out before you treated enough patients.";
        centerText(g, reason, w / 2, h / 2 - 50, WHITE);
        g.setFont(MED);
        centerText(g, "Levels completed: " + (won ? "10 / 10" : (s.getLevel() - 1) + " / 10") + "    Patients rescued: " + s.getHealed() + " / 119", w / 2, h / 2 - 8, WHITE);
        centerText(g, "Score: " + s.getScore() + (won ? "   (bonus +" + s.getFinalBonus() + ")" : ""), w / 2, h / 2 + 26, GOLD);
        centerText(g, "Best this session: " + best, w / 2, h / 2 + 60, WHITE);
        if (((int) (clock * 1.6)) % 2 == 0) {
            g.setFont(BOLD);
            centerText(g, "R: play again      Enter: title screen", w / 2, h / 2 + 112, GOLD);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void drawIcon(Graphics2D g, BufferedImage img, int x, int y, int size, float alpha) {
        java.awt.Composite old = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.SrcOver.derive(alpha));
        Object hint = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, x, y, size, size, null);
        if (hint != null) g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, hint);
        g.setComposite(old);
    }

    private static void heart(Graphics2D g, double cx, double cy, double size, Color c) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx, cy + size * 0.9);
        p.curveTo(cx - size * 1.5, cy - size * 0.1, cx - size * 0.6, cy - size * 1.1, cx, cy - size * 0.35);
        p.curveTo(cx + size * 0.6, cy - size * 1.1, cx + size * 1.5, cy - size * 0.1, cx, cy + size * 0.9);
        g.setColor(c);
        g.fill(p);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(0, 0, 0, 120));
        g.draw(p);
        g.setStroke(old);
    }

    static void shadowText(Graphics2D g, String text, int x, int y, Color c) {
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(text, x + 1, y + 1);
        g.setColor(c);
        g.drawString(text, x, y);
    }

    static void centerText(Graphics2D g, String text, int cx, int y, Color c) {
        int x = cx - g.getFontMetrics().stringWidth(text) / 2;
        shadowText(g, text, x, y, c);
    }
}
