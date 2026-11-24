import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/** Builds every scene with its doors and supply points. */
final class SceneFactory {
    private static final double IN_X = 512, IN_Y = 940;   // where you appear inside a building

    private SceneFactory() {}

    static Map<SceneId, Scene> build() throws IOException {
        Map<SceneId, Scene> scenes = new EnumMap<SceneId, Scene>(SceneId.class);

        // ---------------- campus (outdoors)
        Scene campus = new Scene(SceneId.CAMPUS, Assets.image(SceneId.CAMPUS.imageFile), 392, 905);
        campus.addPortal(new Portal(224, 492, 48, 20, SceneId.NSTP_OFFICE, IN_X, IN_Y, "NSTP Office"));
        campus.addPortal(new Portal(224, 672, 48, 20, SceneId.CLINIC, IN_X, IN_Y, "Clinic"));
        campus.addPortal(new Portal(708, 276, 40, 20, SceneId.GYM, IN_X, IN_Y, "Gymnasium"));
        campus.addPortal(new Portal(470, 664, 44, 16, SceneId.MEDIC_STATION, IN_X, IN_Y, "Medic Station"));
        campus.addSupply(new SupplyPoint(500, 692, Config.SUPPLY_RANGE, "Medic Tent"));
        scenes.put(SceneId.CAMPUS, campus);

        // ---------------- interiors (each has one exit door at the bottom)
        scenes.put(SceneId.NSTP_OFFICE, interior(SceneId.NSTP_OFFICE, 248, 534,
                new SupplyPoint(912, 310, Config.SUPPLY_RANGE, "Supply Cabinet")));
        scenes.put(SceneId.CLINIC, interior(SceneId.CLINIC, 248, 728,
                new SupplyPoint(240, 150, Config.SUPPLY_RANGE, "First-Aid Shelf"),
                new SupplyPoint(110, 880, Config.SUPPLY_RANGE, "First-Aid Shelf")));
        scenes.put(SceneId.GYM, interior(SceneId.GYM, 728, 336,
                new SupplyPoint(196, 900, Config.SUPPLY_RANGE, "Gym Medic Post")));
        scenes.put(SceneId.MEDIC_STATION, interior(SceneId.MEDIC_STATION, 490, 704,
                new SupplyPoint(656, 484, Config.SUPPLY_RANGE, "First-Aid Box"),
                new SupplyPoint(60, 484, Config.SUPPLY_RANGE, "First-Aid Box")));

        for (Scene s : scenes.values()) s.finish();
        return scenes;
    }

    private static Scene interior(SceneId id, double exitX, double exitY, SupplyPoint... supplies) throws IOException {
        Scene s = new Scene(id, Assets.image(id.imageFile), IN_X, IN_Y);
        s.addPortal(new Portal(480, 962, 64, 30, SceneId.CAMPUS, exitX, exitY, "Exit to Campus"));
        for (SupplyPoint sp : supplies) s.addSupply(sp);
        return s;
    }
}
