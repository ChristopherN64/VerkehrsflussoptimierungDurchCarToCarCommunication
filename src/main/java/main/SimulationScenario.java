package main;

public enum SimulationScenario {
    DREISPURIGE_AUTOBAHN("dreispurige_autobahn"),
    LIEGENGEBLIEBENES_FAHRZEUG("liegengebliebenes_fahrzeug"),
    ENDE_EINER_SPUR("ende_einer_spur"),
    ENDE_VON_ZWEI_SPUREN("ende_von_zwei_spuren");

    public final String folder;
    private SimulationScenario(String folder) {this.folder = folder;}
}
