/** Every playable location, with its background image and collision map. */
enum SceneId {
    CAMPUS("Campus", "MAPS/campus_map.png"),
    CLINIC("Clinic", "MAPS/clinic.png"),
    GYM("Gymnasium", "MAPS/gymnasium.png"),
    MEDIC_STATION("Medic Station", "MAPS/medic_station.png"),
    NSTP_OFFICE("NSTP Office", "MAPS/nstp_office_floor1.png");

    final String title;
    final String imageFile;

    SceneId(String title, String imageFile) {
        this.title = title;
        this.imageFile = imageFile;
    }

    /** Collision rows ('#' = blocked, '.' = free) for this location. */
    String[] grid() {
        switch (this) {
            case CAMPUS:        return MapData.CAMPUS;
            case CLINIC:        return MapData.CLINIC;
            case GYM:           return MapData.GYM;
            case MEDIC_STATION: return MapData.MEDIC_STATION;
            case NSTP_OFFICE:   return MapData.NSTP_OFFICE;
            default: throw new IllegalStateException("No map for " + this);
        }
    }
}
