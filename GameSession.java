import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** All game rules and state for one shift (model). Rendering lives in GamePanel / Hud. */
final class GameSession {
    enum Result { RUNNING, WON, LOST_LIVES, LOST_TIME }

    /** Points the on-screen arrow at the most useful target. */
    static final class Hint {
        final double x, y;
        final String label;
        final boolean sameScene;

        Hint(double x, double y, String label, boolean sameScene) {
            this.x = x; this.y = y; this.label = label; this.sameScene = sameScene;
        }
    }

    private static final double FADE = 0.22;   // seconds for each half of a door transition

    private static final class Transition {
        final Portal portal;
        double t;
        boolean switched;

        Transition(Portal portal) { this.portal = portal; }
    }

    private final Map<SceneId, Scene> scenes;
    private final Player player;
    private final Sprite injuredSprite, healedSprite;
    private final List<Patient> patients = new ArrayList<Patient>();
    private final List<FloatingText> floats = new ArrayList<FloatingText>();
    private final Random rng = new Random();

    private Scene current;
    private Result result = Result.RUNNING;
    private int score, healed, lives = Config.START_LIVES, kits = Config.MAX_KITS, finalBonus;
    private double timeLeft = Config.SHIFT_SECONDS, elapsed;
    private int level = 1, levelTarget = 3, levelRescued, spawnedThisLevel;
    private double portalCooldown, toastTime, hitFlash;
    private double spawnCooldown, levelCongratsTime;
    private String levelCongratsText = "";
    private String toast = "";
    private Transition transition;
    private boolean sceneChanged;
    private Patient dialoguePatient;
    private int dialogueStep;
    private String dialogueFeedback = "";
    private double dialogueFeedbackTime;
    private boolean companionAngry;

    GameSession(Map<SceneId, Scene> scenes, PlayerSkin skin, Sprite injured, Sprite healed) {
        this.scenes = scenes;
        this.injuredSprite = injured;
        this.healedSprite = healed;
        this.current = scenes.get(SceneId.CAMPUS);
        this.player = new Player(skin, current.spawnX, current.spawnY);
        spawnNextWavePatients();
        showToast("Level 1: rescue 3 patients. Talk, decide, then press E to treat.", 5.0);
    }

    // ------------------------------------------------------------------ accessors

    Scene getScene() { return current; }
    Player getPlayer() { return player; }
    Result getResult() { return result; }
    int getScore() { return score; }
    int getHealed() { return healed; }
    int getLevel() { return level; }
    int getLevelTarget() { return levelTarget; }
    int getLevelRescued() { return levelRescued; }
    int getLives() { return lives; }
    int getKits() { return kits; }
    int getFinalBonus() { return finalBonus; }
    double getTimeLeft() { return Math.max(0, timeLeft); }
    String getToast() { return toast; }
    double getToastTime() { return toastTime; }
    double getHitFlash() { return hitFlash; }
    double getLevelCongratsTime() { return levelCongratsTime; }
    String getLevelCongratsText() { return levelCongratsText; }

    List<Patient> patientsIn(Scene scene) {
        List<Patient> out = new ArrayList<Patient>();
        for (Patient p : patients) {
            if (p.getSceneId() == scene.id) out.add(p);
        }
        return out;
    }

    List<FloatingText> floatsIn(Scene scene) {
        List<FloatingText> out = new ArrayList<FloatingText>();
        for (FloatingText f : floats) {
            if (f.scene == scene.id) out.add(f);
        }
        return out;
    }

    int waitingIn(SceneId id) {
        int n = 0;
        for (Patient p : patients) {
            if (p.isWaiting() && p.getSceneId() == id) n++;
        }
        return n;
    }

    int waitingTotal() {
        int n = 0;
        for (Patient p : patients) {
            if (p.isWaiting()) n++;
        }
        return n;
    }

    /** 0 = nothing happening, 1 = screen fully black (door transition). */
    double fadeAlpha() {
        if (transition == null) return 0;
        double a = transition.t < FADE ? transition.t / FADE : (2 * FADE - transition.t) / FADE;
        return Math.max(0, Math.min(1, a));
    }

    /** True once after the player changed rooms (the camera should jump, not glide). */
    boolean consumeSceneChanged() {
        boolean r = sceneChanged;
        sceneChanged = false;
        return r;
    }

    // ------------------------------------------------------------------ update

    void update(double dt, KeyHandler in) {
        for (FloatingText f : floats) f.update(dt);
        floats.removeIf(FloatingText::isDone);
        if (hitFlash > 0) hitFlash = Math.max(0, hitFlash - dt);
        if (toastTime > 0) toastTime = Math.max(0, toastTime - dt);
        if (levelCongratsTime > 0) {
            levelCongratsTime = Math.max(0, levelCongratsTime - dt);
            return; // give the player a short celebration break between levels
        }

        if (result != Result.RUNNING) return;

        if (dialoguePatient != null) {
            handleDialogue(in);
            return; // freeze the shift and other patients while the player makes a decision
        }

        elapsed += dt;
        timeLeft -= dt;
        spawnCooldown = Math.max(0, spawnCooldown - dt);
        portalCooldown = Math.max(0, portalCooldown - dt);

        if (transition != null) {
            advanceTransition(dt);
        } else {
            handlePlayer(dt, in);
        }
        player.update(dt);

        updatePatients(dt);
        spawnNextWavePatients();
        checkEnd();
    }

    private void handlePlayer(double dt, KeyHandler in) {
        Patient target = nearestWaitingPatient();
        boolean pressed = in.interactPressed();

        if (pressed && target != null) {
            if (kits <= 0) {
                showToast("No first-aid kits! Restock at a supply point (E).", 2.5);
            } else if (target.isReadyForTreatment()) {
                completeTreatment(target);
                return;
            } else {
                beginDialogue(target);
                return;
            }
        } else if (pressed && current.supplyNear(player.getX(), player.getY()) != null) {
            restock();
        }

        double ix = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
        double iy = (in.downKey() ? 1 : 0) - (in.up() ? 1 : 0);
        player.move(dt, ix, iy, in.sprint(), current);

        Portal portal = current.portalAt(player.getX(), player.getY());
        if (portal != null && portalCooldown <= 0) {
            transition = new Transition(portal);
        }
    }

    private void beginDialogue(Patient patient) {
        dialoguePatient = patient;
        dialogueStep = 0;
        dialogueFeedback = "";
        dialogueFeedbackTime = 0;
        companionAngry = false;
        player.halt();
    }

    private void handleDialogue(KeyHandler in) {
        int choice = -1;
        if (in.consume(java.awt.event.KeyEvent.VK_1) || in.consume(java.awt.event.KeyEvent.VK_NUMPAD1)) choice = 0;
        else if (in.consume(java.awt.event.KeyEvent.VK_2) || in.consume(java.awt.event.KeyEvent.VK_NUMPAD2)) choice = 1;
        else if (in.consume(java.awt.event.KeyEvent.VK_3) || in.consume(java.awt.event.KeyEvent.VK_NUMPAD3)) choice = 2;
        if (choice < 0) return;

        DialogueScenario.Question q = DialogueScenario.get(level, dialoguePatient.getScenarioNumber(), dialogueStep);
        if (choice == q.correct) {
            score += 25;
            floats.add(new FloatingText(current.id, player.getX(), player.getY() - 48, "+25", new Color(120, 255, 140)));
            dialogueFeedback = q.explanation;
            companionAngry = false;
            dialogueStep++;
            if (dialogueStep >= DialogueScenario.size()) {
                Patient cleared = dialoguePatient;
                cleared.markReadyForTreatment();
                dialoguePatient = null;
                dialogueStep = 0;
                dialogueFeedback = "All decisions correct! Press E beside the patient to treat them.";
                showToast("Good decisions! Now press E to treat the patient.", 3.0);
            }
        } else {
            score = Math.max(0, score - 15);
            companionAngry = true;
            dialogueFeedback = "That choice is not safe. Try again. " + q.explanation;
            showToast("The companion is upset. -15 points. Choose again.", 2.5);
        }
        dialogueFeedbackTime = 3.0;
    }

    boolean isDialogueOpen() { return dialoguePatient != null; }
    int dialogueStep() { return dialogueStep; }
    String dialogueSpeaker() {
        if (dialoguePatient == null) return "";
        String name = DialogueScenario.get(level, dialoguePatient.getScenarioNumber(), Math.min(dialogueStep, DialogueScenario.size()-1)).speaker;
        return companionAngry ? name + " (angry!)" : name;
    }
    String dialogueLine() {
        return dialoguePatient == null ? "" : DialogueScenario.get(level, dialoguePatient.getScenarioNumber(), Math.min(dialogueStep, DialogueScenario.size()-1)).line;
    }
    String[] dialogueChoices() {
        return dialoguePatient == null ? new String[0] : DialogueScenario.get(level, dialoguePatient.getScenarioNumber(), Math.min(dialogueStep, DialogueScenario.size()-1)).choices;
    }
    boolean companionAngry() { return companionAngry; }
    String dialogueFeedback() { return dialogueFeedback; }

        private Patient nearestWaitingPatient() {
        Patient best = null;
        double bestD = Config.TREAT_RANGE;
        for (Patient p : patients) {
            if (!p.isWaiting() || p.getSceneId() != current.id) continue;
            double d = p.distanceTo(player.getX(), player.getY());
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** The patient the player could treat right now (for the on-screen prompt), or null. */
    Patient treatablePatient() {
        return transition == null ? nearestWaitingPatient() : null;
    }

    boolean nearSupply() {
        return transition == null && current.supplyNear(player.getX(), player.getY()) != null;
    }

    private void completeTreatment(Patient p) {
        p.markHealed();
        kits--;
        healed++;
        levelRescued++;
        int points = 100 + (int) Math.round(100 * p.urgency());
        score += points;
        floats.add(new FloatingText(p.getSceneId(), p.getX(), p.getY() - 46, "+" + points, new Color(120, 255, 140)));
        showToast("Patient treated!  +" + points, 2.0);
        if (levelRescued >= levelTarget) completeLevel();
        else spawnNextWavePatients();
    }

    private void restock() {
        if (kits >= Config.MAX_KITS) {
            showToast("Your kit bag is already full.", 1.8);
            return;
        }
        kits = Config.MAX_KITS;
        floats.add(new FloatingText(current.id, player.getX(), player.getY() - 50, "Kits restocked", new Color(120, 200, 255)));
        showToast("First-aid kits restocked!", 2.0);
    }

    private void advanceTransition(double dt) {
        player.halt();
        transition.t += dt;
        if (!transition.switched && transition.t >= FADE) {
            Portal p = transition.portal;
            current = scenes.get(p.target);
            player.setPosition(p.spawnX, p.spawnY);
            transition.switched = true;
            portalCooldown = 0.7;
            sceneChanged = true;
        }
        if (transition.t >= 2 * FADE) transition = null;
    }

    private void updatePatients(double dt) {
        for (Patient p : patients) {
            p.update(dt);
            if (p.pollLost()) {
                // A missed patient costs a life, but does not make the level quota impossible.
                spawnedThisLevel = Math.max(levelRescued, spawnedThisLevel - 1);
                lives--;
                hitFlash = 0.6;
                floats.add(new FloatingText(p.getSceneId(), p.getX(), p.getY() - 40, "Too late!", new Color(255, 90, 90)));
                showToast("A patient was not treated in time!  -1 life", 3.0);
            }
        }
        patients.removeIf(Patient::isFinished);
        spawnNextWavePatients();
    }

    private void spawnNextWavePatients() {
        int activeLimit = 2;
        if (spawnCooldown > 0 || spawnedThisLevel >= levelTarget || waitingTotal() >= activeLimit) return;
        {
            SceneId destination = spawnedThisLevel == 0 ? SceneId.CAMPUS : pickScene(null);
            if (!spawnPatient(destination)) {
                if (!spawnPatient(pickScene(destination))) return;
            }
            spawnedThisLevel++;
            spawnCooldown = 3.8; // stagger new arrivals so the player has time to respond
        }
    }

    private void completeLevel() {
        int bonusSeconds = 90;
        timeLeft += bonusSeconds;
        if (level >= 10) {
            finalBonus = (int) Math.max(0, timeLeft) * 3 + lives * 150;
            score += finalBonus;
            result = Result.WON;
            return;
        }
        level++;
        levelTarget = level == 10 ? 20 : 2 * level + 1;
        levelRescued = 0;
        spawnedThisLevel = 0;
        levelCongratsText = "CONGRATULATIONS!  LEVEL " + (level - 1) + " COMPLETE!";
        levelCongratsTime = 4.8;
        spawnCooldown = 1.0;
        showToast("Level " + level + " begins! +1:30 time bonus. Rescue " + levelTarget + " patients!", 4.0);
    }

    private SceneId pickScene(SceneId avoid) {
        SceneId[] ids = SceneId.values();
        double[] weights = new double[ids.length];
        double sum = 0;
        for (int i = 0; i < ids.length; i++) {
            weights[i] = ids[i] == SceneId.CAMPUS ? 3.0 : 1.3;
            if (ids[i] == avoid) weights[i] = 0;
            sum += weights[i];
        }
        double r = rng.nextDouble() * sum;
        for (int i = 0; i < ids.length; i++) {
            r -= weights[i];
            if (r <= 0 && weights[i] > 0) return ids[i];
        }
        return SceneId.CAMPUS;
    }

    private boolean spawnPatient(SceneId id) {
        Scene sc = scenes.get(id);
        List<double[]> spots = sc.patientSpots();
        if (spots.isEmpty()) return false;
        for (int attempt = 0; attempt < 80; attempt++) {
            double[] s = spots.get(rng.nextInt(spots.size()));
            if (!spotIsFree(sc, s[0], s[1])) continue;
            double basePatience = id == SceneId.CAMPUS ? Config.PATIENCE_CAMPUS : Config.PATIENCE_INDOOR;
            // Patient time is generous at the start and decreases gently with each level.
            double patience = Math.max(72, basePatience + 45 - (level - 1) * 3.0);
            patients.add(new Patient(id, s[0], s[1], patience, injuredSprite, healedSprite, rng.nextDouble() * 2.0, healed + spawnedThisLevel + 1));
            return true;
        }
        return false;
    }

    private boolean spotIsFree(Scene sc, double x, double y) {
        for (Patient p : patients) {
            if (p.getSceneId() == sc.id && p.distanceTo(x, y) < 90) return false;
        }
        if (sc == current && player.distanceTo(x, y) < 220) return false;
        return true;
    }

    private void checkEnd() {
        if (lives <= 0) {
            lives = 0;
            result = Result.LOST_LIVES;
        } else if (timeLeft <= 0) {
            timeLeft = 0;
            result = Result.LOST_TIME;
        }
    }

    private void showToast(String text, double seconds) {
        toast = text;
        toastTime = seconds;
    }

    // ------------------------------------------------------------------ hint arrow

    Hint hint() {
        if (transition != null) return null;
        Patient best = null;
        double bestD = Double.MAX_VALUE;
        for (Patient p : patients) {
            if (!p.isWaiting() || p.getSceneId() != current.id) continue;
            double d = p.distanceTo(player.getX(), player.getY());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best != null) return new Hint(best.getX(), best.getY() - 10, "Patient", true);

        Patient urgent = null;
        for (Patient p : patients) {
            if (!p.isWaiting()) continue;
            if (urgent == null || p.getPatience() < urgent.getPatience()) urgent = p;
        }
        if (urgent == null) return null;
        Portal door = current.id == SceneId.CAMPUS ? current.portalTo(urgent.getSceneId()) : current.portals.get(0);
        if (door == null) return null;
        String label = current.id == SceneId.CAMPUS ? "Patient in " + urgent.getSceneId().title : "Patient outside";
        return new Hint(door.centerX(), door.centerY(), label, false);
    }

    /** Counts of waiting patients per location (for the HUD). */
    Map<SceneId, Integer> waitingCounts() {
        Map<SceneId, Integer> m = new EnumMap<SceneId, Integer>(SceneId.class);
        for (SceneId id : SceneId.values()) {
            int n = waitingIn(id);
            if (n > 0) m.put(id, n);
        }
        return m;
    }
}
