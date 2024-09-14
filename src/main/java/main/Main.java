package main;

import main.analytics.Analyser;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.HashMap;
import java.util.HashSet;

public class Main {
    static final String SIMULATION_DELAY = "200";
    static final int SIMULATION_STEPS = 1000;
    public static HashMap<String,SimVehicle> vehicles = new HashMap<>();
    public static int step;

    public static void main(String[] args) {

        initSimulation();
        // Schleife für die Simulationsschritte
        System.out.println("Simulation startet...");
        for (step = 0; step < SIMULATION_STEPS; step++) {
            Simulation.step();

            reinitVehicles();

            simulateAllVehicles();
        }

        // Simulation schließen
        Analyser.printAnalytics();
        Simulation.close();
    }

    //Simuliert alle Fahrzeuge auf der Karte
    private static void simulateAllVehicles() {
        vehicles.values().forEach(SimVehicle::simulateStep);
    }

    //Synchronisiert die Fahrzeuge der Sumo-Simulation mit der Liste an Fahrzeugen
    private static void reinitVehicles() {
        HashSet<String> vehicleIds = new HashSet<>(Vehicle.getIDList());
        //Add new Vehicles
        vehicleIds.forEach(vehicleId->{
            if(!vehicles.containsKey(vehicleId)) vehicles.put(vehicleId, new SimVehicle(vehicleId));
        });
        //Remove missing Vehicles
        vehicles.values().removeIf(vehicle->{
            if(!vehicleIds.contains(vehicle.getVehicleId())){
                vehicle.setVehicleState(VehicleState.FINISHED);
                Analyser.updateVehicleResult(vehicle);
                return true;
            }
            return false;
        });
    }

    //Initialisiert Simulation
    private static void initSimulation() {
        System.out.println("Initialisiere Simulation");

        System.loadLibrary("libtracijni");

        // Starten der SUMO-GUI-Simulation mit automatischem Start und einem Delay von 500 ms
        Simulation.start(new StringVector(new String[] {
                "sumo-gui",               // SUMO-GUI starten
                "--start",                // Simulation automatisch starten
                "--delay", SIMULATION_DELAY,         // Delay von 500 ms
                "-n", ".\\src\\main\\sumo\\autobahn.net.xml", // Netz-Datei
                "-r", ".\\src\\main\\sumo\\autobahn.rou.xml",  // Routen-Datei
                "--gui-settings-file", ".\\src\\main\\sumo\\gui-settings.xml"
        }));

        System.out.println("Simulations-Initialisierung abgeschlossen");
    }
}
