package main;

import main.analytics.Analyser;
import main.consensus.Consensus;
import main.debugger.Debugger;
import main.flocking.Flocking;
import main.stauerkennung.Stauerkennung;
import main.vehicle.Cache;
import org.eclipse.sumo.libtraci.*;

import java.util.List;

public class Main {
    public static SimulationScenario SIMULATION_SCENARIO;
    public static String version = "validation";
    public static boolean SIMULATE_CONSENSUS = false;
    public static boolean SIMULATE_FLOCKING = false;
    public static final String SIMULATION_DELAY = "0";
    public static int SIMULATION_STEPS = 2;
    public static int step;

    public static void main(String[] args) {
        List<SimulationScenario> simulationScenarios = List.of(SimulationScenario.DREISPURIGE_AUTOBAHN,SimulationScenario.LIEGENGEBLIEBENES_FAHRZEUG,SimulationScenario.ENDE_EINER_SPUR,SimulationScenario.ENDE_VON_ZWEI_SPUREN);

        Stauerkennung.NEIGHBOR_RADIUS_FOR_TRAFFIC = 60;
        Consensus.FLOCKING_DEACTIVATION_COOLDOWN = 60;
        Flocking.MAX_MIN_DISTANCE_DIFF = 1.4;
        Flocking.COHESION_MINIMUM_UTILIZATION_OFFSET_ON_NEW_LANE = 1.4;
        Flocking.COHESION_LANE_CHANGE_COOLDOWN = 10;
        Flocking.seperationPercentageWhenAligmentAcellarate = 0.8;


        simulationScenarios.forEach(simulationScenario -> {
            SIMULATE_CONSENSUS = false;
            SIMULATE_FLOCKING = false;
            SIMULATION_SCENARIO = simulationScenario;
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }

            SIMULATE_FLOCKING=true;

            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            SIMULATE_CONSENSUS = true;

            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        SIMULATION_STEPS = 3;
        simulationScenarios.forEach(simulationScenario -> {
            SIMULATE_CONSENSUS = false;
            SIMULATE_FLOCKING = false;
            SIMULATION_SCENARIO = simulationScenario;
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }

            SIMULATE_FLOCKING=true;

            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            SIMULATE_CONSENSUS = true;

            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                simulateScenario(SIMULATION_SCENARIO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });


    }

    public static void simulateScenario(SimulationScenario scenario) {
        initSimulation(scenario);
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
        stopSimulation();
        Analyser.printAnalytics();
    }

    //Simuliert alle Fahrzeuge auf der Karte
    private static void simulateAllVehicles() {
        Cache.vehicles.values().forEach(v -> {
            try {
                v.simulateStep();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    //Initialisiert Simulation
    private static void initSimulation(SimulationScenario scenario) {
        System.out.println("Initialisiere Simulation");

        System.loadLibrary("libtracijni");

        // Starten der SUMO-GUI-Simulation mit automatischem Start und einem Delay von 500 ms
        Simulation.start(new StringVector(new String[]{
                "sumo-gui",               // SUMO-GUI starten
                "--start",                // Simulation automatisch starten
                "--delay", SIMULATION_DELAY,         // Delay von 500 ms
                "-c", ".\\src\\main\\sumo\\" + scenario.folder + "\\sumo_setting.sumocfg",  // Konfigurationsdatei
                "--quit-on-end"
        }));

        System.out.println("Simulations-Initialisierung abgeschlossen");
    }

    private static void stopSimulation() {
        Simulation.close();
        System.out.println("Simulation beendet.");
    }
}
