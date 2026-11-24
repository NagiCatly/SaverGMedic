import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The four walking animations of one playable medic. */
final class PlayerSkin {
    final String name;
    final Sprite up, down, left, right;
    final double scale;      // world pixels per sprite pixel

    private PlayerSkin(String name, Sprite up, Sprite down, Sprite left, Sprite right, double scale) {
        this.name = name;
        this.up = up; this.down = down; this.left = left; this.right = right;
        this.scale = scale;
    }

    Sprite forFacing(Direction d) {
        switch (d) {
            case UP:    return up;
            case LEFT:  return left;
            case RIGHT: return right;
            case DOWN:
            default:    return down;
        }
    }

    static List<PlayerSkin> loadAll() throws IOException {
        List<PlayerSkin> skins = new ArrayList<PlayerSkin>();
        skins.add(new PlayerSkin("Medic Alex",
                Assets.gif("CHARACTER/1/WALKING UP.gif", false, 0),
                Assets.gif("CHARACTER/1/WALKING DOWN.gif", false, 0),
                Assets.gif("CHARACTER/1/WALKING LEFT.gif", false, 0),
                Assets.gif("CHARACTER/1/WALKING RIGHT.gif", false, 0),
                1.3));
        skins.add(new PlayerSkin("Medic Sam",
                Assets.gif("CHARACTER/2/W.gif", true, 0.6),
                Assets.gif("CHARACTER/2/S.gif", true, 0.6),
                Assets.gif("CHARACTER/2/A.gif", true, 0.6),
                Assets.gif("CHARACTER/2/D.gif", true, 0.6),
                0.5));
        return skins;
    }
}
