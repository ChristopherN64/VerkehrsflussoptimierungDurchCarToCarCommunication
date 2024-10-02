package main;

public enum SimulationSzenario {
    ENDE_EINER_SPUR("ende_einer_spur"),
    DREISPURIGE_AUTOBAHN("dreispurige_autobahn"),
    CIRCLE("circle"),
    BAUSTELLE("baustelle"),;

    public final String folder;
    private SimulationSzenario(String folder) {this.folder = folder;}
}
