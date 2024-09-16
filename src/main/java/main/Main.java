package main;

import main.analytics.Analyser;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.sumo.libtraci.*;

import java.util.*;

public class Main {
    static final String SIMULATION_DELAY = "200";
    static final int SIMULATION_STEPS = 1000;
    public static HashMap<String,SimVehicle> vehicles = new HashMap<>();
    public static double cellSizeX = 200;
    public static double cellSizeY = 20;
    public static Map<String, List<SimVehicle>> grid = new HashMap<>();
    public static HashMap<Pair<String,String>,Double> distances = new HashMap<>();
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
        grid = new HashMap<>();
        //Add new Vehicles and preprocess
        vehicleIds.forEach(vehicleId->{
            if(!vehicles.containsKey(vehicleId)) {
                vehicles.put(vehicleId, new SimVehicle(vehicleId));
            }
            TraCIPosition position = Vehicle.getPosition(vehicleId);
            SimVehicle vehicle = vehicles.get(vehicleId);
            vehicle.setPosition(new MutablePair<>(position.getX(),position.getY()));
            addVehicle(vehicle);
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



    public static String getCellKey(double x, double y) {
        int cellX = (int) (x / cellSizeX);
        int cellY = (int) (y / cellSizeY);
        return cellX + "," + cellY;
    }

    public static void addVehicle(SimVehicle vehicle) {
        String cellKey = getCellKey(vehicle.getPosition().getLeft(), vehicle.getPosition().getRight());
        grid.putIfAbsent(cellKey, new ArrayList<>());
        grid.get(cellKey).add(vehicle);
    }

    public static List<SimVehicle> getNeighbors(String vehicleId, double radius) {
        List<SimVehicle> neighbors = new ArrayList<>();
        SimVehicle vehicle = vehicles.get(vehicleId);
        String cellKey = getCellKey(vehicle.getPosition().getLeft(), vehicle.getPosition().getRight());
        String[] keyParts = cellKey.split(",");
        int cellX = Integer.parseInt(keyParts[0]);
        int cellY = Integer.parseInt(keyParts[1]);

        // Durchsuche aktuelle und angrenzende Zellen
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                String neighborKey = (cellX + dx) + "," + (cellY + dy);
                if (grid.containsKey(neighborKey)) {
                    for (SimVehicle neighbor : grid.get(neighborKey)) {
                        if (calculateDistance(vehicle, neighbor) <= radius) {
                            neighbors.add(neighbor);
                        }
                    }
                }
            }
        }
        return neighbors;
    }

    private static double calculateDistance(SimVehicle v1, SimVehicle v2) {
        double dx = v1.getPosition().getLeft() - v2.getPosition().getLeft();
        double dy =  v1.getPosition().getRight() - v2.getPosition().getRight();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public void debugGrid(){
        vehicles.values().forEach(v->Vehicle.setColor(v.getVehicleId(),new TraCIColor(0,255,0)));
        if( grid.get("0,0")!=null) grid.get("0,0").forEach(v->
                Vehicle.setColor(v.getVehicleId(), new TraCIColor(0,0,255))
        );
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
