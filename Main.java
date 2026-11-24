import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Campus Medic - NSTP First-Aid Rescue.
 *
 * Compile:  javac *.java
 * Run:      java Main            (run it from inside the OOP folder, or pass the folder path as an argument)
 */
public class Main {
    public static void main(String[] args) {
        final String hint = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                launch(hint);
            }
        });
    }

    private static void launch(String assetHint) {
        if (!Assets.locate(assetHint)) {
            JOptionPane.showMessageDialog(null,
                    "Could not find the game images (MAPS/campus_map.png).\n\n"
                            + "Run the game from inside the OOP folder, or give the folder path:\n"
                            + "    java Main \"C:\\path\\to\\OOP\"",
                    "Campus Medic", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        try {
            Map<SceneId, Scene> scenes = SceneFactory.build();
            List<PlayerSkin> skins = PlayerSkin.loadAll();
            BufferedImage kitIcon = Assets.image("medic_tent_32x32.png");
            Sprite injured = Assets.gif("CHARACTER/patient/injured.gif", true, 0.43);
            Sprite healed = Assets.gif("CHARACTER/patient/healed.gif", true, 0.19);

            JFrame frame = new JFrame("Campus Medic - NSTP First-Aid Rescue");
            GamePanel panel = new GamePanel(scenes, skins, kitIcon, injured, healed);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.requestFocusInWindow();
            panel.start();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "The game could not start:\n" + ex,
                    "Campus Medic", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
