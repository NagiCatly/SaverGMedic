import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Finds the OOP asset folder and loads PNG / GIF files (with proper GIF frame handling). */
final class Assets {
    private static final String MARKER = "campus_map.png";
    private static final Map<String, BufferedImage> CACHE = new HashMap<String, BufferedImage>();
    private static File root;

    private Assets() {}

    /** Looks for the game asset root, with maps either in the root or MAPS folder. */
    static boolean locate(String hint) {
        List<File> candidates = new ArrayList<File>();
        if (hint != null) candidates.add(new File(hint));
        String prop = System.getProperty("oop.assets");
        if (prop != null) candidates.add(new File(prop));
        File wd = new File(System.getProperty("user.dir", "."));
        addWithRelatives(candidates, wd);
        try {
            File code = new File(Assets.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            addWithRelatives(candidates, code.isFile() ? code.getParentFile() : code);
        } catch (Exception ignored) {
            // no code source available - the working directory candidates are enough
        }
        for (File c : candidates) {
            if (c != null) {
                // Support both the original flat layout and the organized MAPS/ folder.
                if (new File(c, MARKER).isFile()) {
                    root = c.getAbsoluteFile();
                    return true;
                }
                if (new File(c, "MAPS/" + MARKER).isFile()) {
                    root = c.getAbsoluteFile();
                    return true;
                }
            }
        }
        return false;
    }

    private static void addWithRelatives(List<File> out, File dir) {
        if (dir == null) return;
        out.add(dir);
        out.add(new File(dir, "OOP"));
        out.add(new File(dir, "assets"));
        File parent = dir.getAbsoluteFile().getParentFile();
        if (parent != null) {
            out.add(parent);
            out.add(new File(parent, "OOP"));
        }
    }

    static File root() { return root; }

    /** Loads a PNG/JPG as a fast, cached image. */
    static BufferedImage image(String rel) throws IOException {
        BufferedImage cached = CACHE.get(rel);
        if (cached != null) return cached;
        File f = new File(root, rel);
        BufferedImage src = ImageIO.read(f);
        if (src == null) throw new IOException("Cannot read image: " + f);
        BufferedImage out = copy(src, src.getTransparency() == BufferedImage.OPAQUE
                ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        CACHE.put(rel, out);
        return out;
    }

    /**
     * Loads an animated GIF.
     *
     * @param keyBackground remove the (near-)white background some of the GIFs were exported with
     * @param bakeFactor    if &gt; 0 every frame is scaled by this factor once, at load time (high quality)
     */
    static Sprite gif(String rel, boolean keyBackground, double bakeFactor) throws IOException {
        File f = new File(root, rel);
        List<Integer> delays = new ArrayList<Integer>();
        List<BufferedImage> frames = readGifFrames(f, delays);

        if (keyBackground) {
            for (int i = 0; i < frames.size(); i++) frames.set(i, keyOutBackground(frames.get(i)));
        }
        cropToContent(frames);
        if (bakeFactor > 0) {
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage fr = frames.get(i);
                int tw = Math.max(1, (int) Math.round(fr.getWidth() * bakeFactor));
                int th = Math.max(1, (int) Math.round(fr.getHeight() * bakeFactor));
                frames.set(i, scaleSmooth(fr, tw, th));
            }
        }
        return new Sprite(frames, delays, bakeFactor > 0);
    }

    // ------------------------------------------------------------------ GIF decoding

    private static List<BufferedImage> readGifFrames(File file, List<Integer> delaysOut) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("No GIF reader available");
        ImageReader reader = readers.next();
        List<BufferedImage> result = new ArrayList<BufferedImage>();
        ImageInputStream in = ImageIO.createImageInputStream(file);
        if (in == null) throw new IOException("Cannot open " + file);
        try {
            reader.setInput(in, false, false);
            int count = reader.getNumImages(true);
            int sw = 0, sh = 0;
            try {
                IIOMetadata sm = reader.getStreamMetadata();
                if (sm != null) {
                    Node lsd = child(sm.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                    sw = attrInt(lsd, "logicalScreenWidth", 0);
                    sh = attrInt(lsd, "logicalScreenHeight", 0);
                }
            } catch (Exception ignored) {
                // fall back to the first frame size
            }
            BufferedImage canvas = null;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                if (canvas == null) {
                    if (sw <= 0 || sh <= 0) { sw = frame.getWidth(); sh = frame.getHeight(); }
                    canvas = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
                }
                int left = 0, top = 0, delay = 100;
                String disposal = "none";
                try {
                    Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    Node desc = child(tree, "ImageDescriptor");
                    left = attrInt(desc, "imageLeftPosition", 0);
                    top = attrInt(desc, "imageTopPosition", 0);
                    Node gce = child(tree, "GraphicControlExtension");
                    if (gce != null) {
                        delay = attrInt(gce, "delayTime", 10) * 10;
                        String d = attr(gce, "disposalMethod");
                        if (d != null) disposal = d;
                    }
                } catch (Exception ignored) {
                    // keep the defaults
                }
                BufferedImage backup = "restoreToPrevious".equals(disposal) ? copy(canvas, BufferedImage.TYPE_INT_ARGB) : null;
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, left, top, null);
                g.dispose();
                result.add(copy(canvas, BufferedImage.TYPE_INT_ARGB));
                delaysOut.add(delay <= 0 ? 100 : delay);

                if ("restoreToBackgroundColor".equals(disposal)) {
                    Graphics2D cg = canvas.createGraphics();
                    cg.setComposite(AlphaComposite.Clear);
                    cg.fillRect(left, top, frame.getWidth(), frame.getHeight());
                    cg.dispose();
                } else if (backup != null) {
                    canvas = backup;
                }
            }
        } finally {
            in.close();
            reader.dispose();
        }
        if (result.isEmpty()) throw new IOException("GIF has no frames: " + file);
        return result;
    }

    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) return n;
        }
        return null;
    }

    private static String attr(Node node, String name) {
        if (node == null) return null;
        NamedNodeMap map = node.getAttributes();
        if (map == null) return null;
        Node a = map.getNamedItem(name);
        return a == null ? null : a.getNodeValue();
    }

    private static int attrInt(Node node, String name, int def) {
        String v = attr(node, name);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    // ------------------------------------------------------------------ image helpers

    private static BufferedImage copy(BufferedImage src, int type) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), type);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static boolean isLight(int argb) {
        int a = argb >>> 24;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return a > 0 && r >= 200 && g >= 200 && b >= 200;
    }

    private static boolean isFringe(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        return mn >= 165 && (mx - mn) <= 24;
    }

    /** Makes the white backdrop transparent by flood-filling inwards from the image border. */
    static BufferedImage keyOutBackground(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        boolean[] bg = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        for (int x = 0; x < w; x++) { seed(px, bg, queue, x); seed(px, bg, queue, (h - 1) * w + x); }
        for (int y = 0; y < h; y++) { seed(px, bg, queue, y * w); seed(px, bg, queue, y * w + w - 1); }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w, y = i / w;
            if (x > 0) seed(px, bg, queue, i - 1);
            if (x < w - 1) seed(px, bg, queue, i + 1);
            if (y > 0) seed(px, bg, queue, i - w);
            if (y < h - 1) seed(px, bg, queue, i + w);
        }
        // two passes to eat the light anti-aliasing halo next to the removed background
        for (int pass = 0; pass < 2; pass++) {
            List<Integer> halo = new ArrayList<Integer>();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (bg[i] || !isFringe(px[i])) continue;
                    boolean touches = (x > 0 && bg[i - 1]) || (x < w - 1 && bg[i + 1])
                            || (y > 0 && bg[i - w]) || (y < h - 1 && bg[i + w]);
                    if (touches) halo.add(i);
                }
            }
            for (int i : halo) bg[i] = true;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < px.length; i++) {
            if (bg[i]) px[i] = 0;
        }
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    private static void seed(int[] px, boolean[] bg, ArrayDeque<Integer> queue, int i) {
        if (!bg[i] && isLight(px[i])) {
            bg[i] = true;
            queue.add(i);
        }
    }

    /** Crops all frames to the same rectangle: the union of every frame's visible pixels. */
    private static void cropToContent(List<BufferedImage> frames) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (BufferedImage f : frames) {
            int w = f.getWidth(), h = f.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((f.getRGB(x, y) >>> 24) > 16) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
        }
        if (maxX < 0) return;   // completely empty - leave untouched
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage f = frames.get(i);
            BufferedImage c = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = c.createGraphics();
            g.drawImage(f, -minX, -minY, null);
            g.dispose();
            frames.set(i, c);
        }
    }

    /** High quality downscale (repeated halving, then a final bilinear step). */
    private static BufferedImage scaleSmooth(BufferedImage src, int tw, int th) {
        BufferedImage cur = src;
        while (cur.getWidth() > tw * 2 || cur.getHeight() > th * 2) {
            cur = resize(cur, Math.max(tw, cur.getWidth() / 2), Math.max(th, cur.getHeight() / 2));
        }
        return resize(cur, tw, th);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
