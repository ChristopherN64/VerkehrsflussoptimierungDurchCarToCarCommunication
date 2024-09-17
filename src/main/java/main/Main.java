package main;

import main.analytics.Analyser;
import main.debugger.Debugger;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleGrid;
import org.eclipse.sumo.libtraci.*;

public class Main {
    static final String SIMULATION_DELAY = "200";
    static final int SIMULATION_STEPS = 1000;
    public static int step;

    public static void main(String[] args) {

        initSimulation();
        // Schleife für die Simulationsschritte
        System.out.println("Simulation startet...");
        for (step = 0; step < SIMULATION_STEPS; step++) {
            Simulation.step();

            VehicleGrid.reinitVehicleGrid();

            simulateAllVehicles();

            Debugger.debugVehicleState();
        }

        // Simulation schließen
        Analyser.printAnalytics();
        stopSimulationAndSumo();
    }

    //Simuliert alle Fahrzeuge auf der Karte
    private static void simulateAllVehicles() {
        VehicleGrid.vehicles.values().forEach(SimVehicle::simulateStep);
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

    private static void stopSimulationAndSumo(){
        Simulation.close();
        System.out.println("Simulation beendet.");
    }
}
