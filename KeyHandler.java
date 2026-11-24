import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/** Keyboard state. Only ever touched from the Swing event thread. */
final class KeyHandler extends KeyAdapter {
    private final Set<Integer> down = new HashSet<Integer>();
    private final Set<Integer> pressed = new HashSet<Integer>();

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (down.add(k)) {
            pressed.add(k);   // ignore OS key-repeat
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        down.remove(e.getKeyCode());
    }

    boolean isDown(int key) { return down.contains(key); }

    /** True once per physical key press. */
    boolean consume(int key) { return pressed.remove(key); }

    boolean consumeAny(int... keys) {
        boolean hit = false;
        for (int k : keys) {
            if (pressed.remove(k)) hit = true;
        }
        return hit;
    }

    boolean left()  { return isDown(KeyEvent.VK_A) || isDown(KeyEvent.VK_LEFT); }
    boolean right() { return isDown(KeyEvent.VK_D) || isDown(KeyEvent.VK_RIGHT); }
    boolean up()    { return isDown(KeyEvent.VK_W) || isDown(KeyEvent.VK_UP); }
    boolean downKey() { return isDown(KeyEvent.VK_S) || isDown(KeyEvent.VK_DOWN); }
    boolean sprint() { return isDown(KeyEvent.VK_SHIFT); }
    boolean interactHeld() { return isDown(KeyEvent.VK_E) || isDown(KeyEvent.VK_SPACE); }
    boolean interactPressed() { return consumeAny(KeyEvent.VK_E, KeyEvent.VK_SPACE); }

    void endFrame() { pressed.clear(); }

    void clear() {
        down.clear();
        pressed.clear();
    }
}
