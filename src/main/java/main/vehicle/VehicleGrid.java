package main.vehicle;

import main.analytics.Analyser;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.TraCIPosition;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.*;

public class VehicleGrid {
    public static HashMap<String, SimVehicle> vehicles = new HashMap<>();
    public static Map<String, List<SimVehicle>> vehicleGrid = new HashMap<>();
    public static double cellSizeX = 200;
    public static double cellSizeY = 20;

    //Synchronisiert die Fahrzeuge der Sumo-Simulation mit der Liste an Fahrzeugen
    public static void reinitVehicleGrid() {
        HashSet<String> vehicleIds = new HashSet<>(Vehicle.getIDList());
        VehicleGrid.clearVehicleGrid();
        //Add new Vehicles and preprocess
        vehicleIds.forEach(vehicleId->{
            if(!VehicleGrid.vehicles.containsKey(vehicleId)) {
                VehicleGrid.vehicles.put(vehicleId, new SimVehicle(vehicleId));
            }
            TraCIPosition position = Vehicle.getPosition(vehicleId);
            SimVehicle vehicle = VehicleGrid.vehicles.get(vehicleId);
            vehicle.setPosition(new MutablePair<>(position.getX(),position.getY()));
            VehicleGrid.addVehicle(vehicle);
        });

        //Remove missing Vehicles
        VehicleGrid.vehicles.values().removeIf(vehicle->{
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
        vehicleGrid.putIfAbsent(cellKey, new ArrayList<>());
        vehicleGrid.get(cellKey).add(vehicle);
    }

    public static void clearVehicleGrid(){
        vehicleGrid = new HashMap<>();
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
                if (vehicleGrid.containsKey(neighborKey)) {
                    for (SimVehicle neighbor : vehicleGrid.get(neighborKey)) {
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
}
