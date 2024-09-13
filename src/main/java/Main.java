import org.eclipse.sumo.libtraci.*;

public class Main {
    static final String SIMULATION_DELAY = "500";
    static final int SIMULATION_STEPS = 50000;

    public static void main(String[] args) {
        initSimulation();

        // Schleife für die Simulationsschritte
        System.out.println("Simulation startet...");
        for (int step = 0; step < SIMULATION_STEPS; step++) {
            Simulation.step();  // Nächster Simulationsschritt

            // Abrufen der Liste aller Fahrzeuge in der Simulation
            StringVector vehicleIds = Vehicle.getIDList();

            // Für jedes Fahrzeug die Informationen abrufen und ausgeben
            for (int i = 0; i < vehicleIds.size(); i++) {
                String vehicleId = vehicleIds.get(i);

                // Fahrzeugposition (X, Y)
                TraCIPosition position = Vehicle.getPosition(vehicleId);
                double x = position.getX();
                double y = position.getY();

                // Fahrzeuggeschwindigkeit
                double speed = Vehicle.getSpeed(vehicleId);

                // Abstand zum vorausfahrenden Fahrzeug und dessen ID
                StringDoublePair leaderInfo = Vehicle.getLeader(vehicleId, 0.0);
                if (leaderInfo != null) {
                    String leaderId = leaderInfo.getFirst();
                    double distance = leaderInfo.getSecond();

                    // Ausgabe der Informationen
                    System.out.println("Fahrzeug: " + vehicleId);
                    System.out.println("Position: (" + x + ", " + y + ")");
                    System.out.println("Geschwindigkeit: " + speed + " m/s");
                    System.out.println("Vorausfahrendes Fahrzeug: " + leaderId + ", Abstand: " + distance + " m");
                } else {
                    // Kein vorausfahrendes Fahrzeug
                    System.out.println("Fahrzeug: " + vehicleId);
                    System.out.println("Position: (" + x + ", " + y + ")");
                    System.out.println("Geschwindigkeit: " + speed + " m/s");
                    System.out.println("Kein vorausfahrendes Fahrzeug.");
                }

                System.out.println("-------------------------");
            }
        }

        // Simulation schließen
        Simulation.close();
    }

    private static void initSimulation() {
        System.out.println("Initialisiere Simulation");

        System.loadLibrary("libtracijni");

        // Starten der SUMO-GUI-Simulation mit automatischem Start und einem Delay von 500 ms
        Simulation.start(new StringVector(new String[] {
                "sumo-gui",               // SUMO-GUI starten
                "--start",                // Simulation automatisch starten
                "--delay", SIMULATION_DELAY,         // Delay von 500 ms
                "-n", ".\\src\\main\\sumo\\autobahn.net.xml", // Netz-Datei
                "-r", ".\\src\\main\\sumo\\autobahn.rou.xml"  // Routen-Datei
        }));

        System.loadLibrary("Simulations-Initialisierung abgeschlossen");
    }
}
