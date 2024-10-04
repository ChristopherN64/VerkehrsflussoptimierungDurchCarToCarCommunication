package main;

import main.analytics.Analyser;
import main.debugger.Debugger;
import main.flocking.Flocking;
import main.vehicle.SimVehicle;
import main.vehicle.Cache;
import main.vehicle.VehicleState;
import org.eclipse.sumo.libtraci.*;

import java.util.LinkedList;
import java.util.List;

public class Main {
    public static SimulationSzenario SIMULATION_SZENARIO;
    public static String version = "500 steps ohne Konsens mit Alignment";
    public static final boolean SIMULATE_CONSENSUS = false;
    public static boolean SIMULATE_FLOCKING = true;
    static final String SIMULATION_DELAY = "0";
    static final int SIMULATION_STEPS = 500;
    public static int step;

    public static void main(String[] args) {
        List<SimulationSzenario> simulationSzenarios = List.of(SimulationSzenario.DREISPURIGE_AUTOBAHN);

        simulationSzenarios.forEach(simulationSzenario -> {
            SIMULATION_SZENARIO=simulationSzenario;

            Flocking.ALIGNMENT_NEIGHBOUR_RADIUS = 100;
            Flocking.seperationPercent1 = 1;
            Flocking.seperationPercent2 = 0.7;

            for(Flocking.ALIGNMENT_NEIGHBOUR_RADIUS = 100; Flocking.ALIGNMENT_NEIGHBOUR_RADIUS < 200; Flocking.ALIGNMENT_NEIGHBOUR_RADIUS+=20){
                for( Flocking.seperationPercent1 = 0.6; Flocking.seperationPercent1 <=1; Flocking.seperationPercent1 += 0.1){
                        SIMULATE_FLOCKING = true;
                        simulateSzenario(simulationSzenario);
                }
            }
        });
    }

    public static void simulateSzenario(SimulationSzenario szenario){
        initSimulation(szenario);
        Cache.initMap();
        Analyser.init();
        // Schleife für die Simulationsschritte
        System.out.println("Simulation startet...");
        for (step = 0; step < SIMULATION_STEPS; step++) {
            Simulation.step();

            Cache.reinitVehicleGrid();

            simulateAllVehicles();

            Debugger.debugSeparation();
        }

        // Simulation schließen
        stopSimulationAndSumo();
        Analyser.printAnalytics();
    }

    //Simuliert alle Fahrzeuge auf der Karte
    private static void simulateAllVehicles() {
        Cache.vehicles.values().forEach(SimVehicle::simulateStep);
    }


    //Initialisiert Simulation
    private static void initSimulation(SimulationSzenario szenario) {
        System.out.println("Initialisiere Simulation");

        System.loadLibrary("libtracijni");

        // Starten der SUMO-GUI-Simulation mit automatischem Start und einem Delay von 500 ms
        Simulation.start(new StringVector(new String[] {
                "sumo-gui",               // SUMO-GUI starten
                "--start",                // Simulation automatisch starten
                "--delay", SIMULATION_DELAY,         // Delay von 500 ms
                "-c", ".\\src\\main\\sumo\\" + szenario.folder + "\\sumo_setting.sumocfg",  // Konfigurationsdatei
                "--quit-on-end"
        }));

        System.out.println("Simulations-Initialisierung abgeschlossen");
    }

    private static void stopSimulationAndSumo(){
        Simulation.close();
        System.out.println("Simulation beendet.");
    }
}
