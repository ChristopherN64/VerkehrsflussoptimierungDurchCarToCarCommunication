package main.vehicle;

import main.analytics.Analyser;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.*;

import java.util.*;

public class Cache {
    public static HashMap<String, SimVehicle> vehicles = new HashMap<>();
    public static Map<String, List<SimVehicle>> vehicleGrid = new HashMap<>();
    public static List<TraCICollision> collisions = new ArrayList<>();
    public static HashMap<String,Double> roadSpeeds = new HashMap<>();
    public static double cellSizeX = 100;
    public static double cellSizeY = 100;


    public static void initMap() {
        roadSpeeds = new HashMap<>();
        Edge.getIDList().forEach(edgeId-> roadSpeeds.put(edgeId,Lane.getMaxSpeed(Lane.getIDList().stream().filter(laneId->laneId.startsWith(edgeId)).findFirst().get())));
        vehicles = new HashMap<>();
        vehicleGrid = new HashMap<>();
        collisions = new ArrayList<>();
    }

    //Synchronisiert die Fahrzeuge der Sumo-Simulation mit der Liste an Fahrzeugen
    public static void reinitVehicleGrid() {
        HashSet<String> vehicleIds = new HashSet<>(Vehicle.getIDList());

        Cache.clearVehicleGrid();
        //Add new Vehicles and preprocess
        vehicleIds.forEach(vehicleId->{
            if(!Cache.vehicles.containsKey(vehicleId)) Cache.vehicles.put(vehicleId, new SimVehicle(vehicleId));

            TraCIPosition position = Vehicle.getPosition(vehicleId);
            SimVehicle vehicle = Cache.vehicles.get(vehicleId);
            vehicle.setPosition(new MutablePair<>(position.getX(),position.getY()));
            Cache.addVehicle(vehicle);

            double currentSpeed = Vehicle.getSpeed(vehicleId);
            vehicle.setCurrentSpeed(currentSpeed);
            vehicle.setTargetSpeed(currentSpeed);
            vehicle.setMaxVehicleSpeed(Vehicle.getMaxSpeed(vehicleId));
            vehicle.setMaxRoadSpeed(roadSpeeds.get(Vehicle.getRoadID(vehicleId)));

            StringDoublePair leaderWithDistance = Vehicle.getLeader(vehicleId);
            double distance = leaderWithDistance.getSecond();
            if(distance != -1) vehicle.setLeaderWithDistance(new MutablePair<>(Cache.vehicles.get(leaderWithDistance.getFirst()),distance));
            else vehicle.setLeaderWithDistance(null);

            //get lane information
            getLaneInformation(vehicle);
        });

        //Remove collided Vehicles
        Simulation.getCollisions().forEach(traCICollision -> {
            Cache.collisions.add(traCICollision);
            Cache.removeVehicle(Cache.vehicles.get(traCICollision.getCollider()), VehicleState.COLLIDED);
            Cache.removeVehicle(Cache.vehicles.get(traCICollision.getVictim()), VehicleState.COLLIDED);
        });

        //Remove finished Vehicles
        Cache.vehicles.values().removeIf(vehicle->{
            if(!vehicleIds.contains(vehicle.getVehicleId())){
                return setVehicleRemoved(vehicle, VehicleState.FINISHED);
            }
            return false;
        });
    }

    public static void removeVehicle(SimVehicle vehicle,VehicleState vehicleState){
        if(Cache.vehicles.values().remove(vehicle)){
            setVehicleRemoved(vehicle,vehicleState);
        }

    }

    public static boolean setVehicleRemoved(SimVehicle vehicle,VehicleState vehicleState) {
        vehicle.setVehicleState(vehicleState);
        Analyser.updateVehicleResult(vehicle);
        return true;
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

    public static List<MutablePair<SimVehicle,Double>> getNeighbors(String vehicleId, double radius) {
        List<MutablePair<SimVehicle,Double>> neighbors = new ArrayList<>();
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
                        if(!neighbor.getVehicleId().equals(vehicleId)){
                            double distance = calculateDistance(vehicle, neighbor);
                            if (distance <= radius) {
                                neighbors.add(new MutablePair<>(neighbor, distance));
                            }
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

    public static void getLaneInformation(SimVehicle vehicle){
        int lane;
        int numberOfLanes;
        double distanceToLaneEnd;
        String vehicleId = vehicle.getVehicleId();

        // Aktuelle Spur-ID des Fahrzeugs abrufen
        String laneID = Vehicle.getLaneID(vehicleId);

        // Aktueller Spurindex des Fahrzeugs abrufen
        Integer laneIndex = Vehicle.getLaneIndex(vehicleId);
        lane = laneIndex;

        // Aktuellen Edge (Straßenabschnitt) des Fahrzeugs abrufen
        String edgeID = Vehicle.getRoadID(vehicleId);

        // Anzahl der Spuren auf dem aktuellen Edge abrufen
        Integer numLanes =  Edge.getLaneNumber(edgeID);
        numberOfLanes = numLanes;

        // Aktuelle Position des Fahrzeugs auf der Spur abrufen
        Double lanePosition =Vehicle.getLanePosition(vehicleId);

        // Länge der aktuellen Spur abrufen
        Double laneLength = Lane.getLength(laneID);

        // Berechnung der verbleibenden Distanz auf der aktuellen Spur
        double remainingLaneLength = laneLength - lanePosition;

        // Initialisierung der Distanz bis zum tatsächlichen Spurende
        distanceToLaneEnd = remainingLaneLength;

        // Route des Fahrzeugs abrufen
        List<String> route = Vehicle.getRoute(vehicleId).stream().toList();

        // Aktueller Index des Edges in der Route
        int routeIndex = Vehicle.getRouteIndex(vehicleId);
        vehicle.setRouteIndex(routeIndex);

        // Aktuelle Spur als Startpunkt für die Überprüfung setzen
        String currentLaneID = laneID;

        // Schleife über die verbleibenden Edges in der Route
        for (int i = routeIndex + 1; i < route.size(); i++) {
            String nextEdgeID = route.get(i);

            // Verbindungen von der aktuellen Spur abrufen
            List<TraCIConnection> links = Lane.getLinks(currentLaneID).stream().toList();

            boolean foundNextLane = false;

            // Überprüfen, ob es eine Verbindung zu einer Spur auf dem nächsten Edge gibt
            for (TraCIConnection link : links) {
                // Ziel-Spur-ID abrufen
                String toLaneID = link.getApproachedLane();

                // Edge-ID der Zielspur abrufen
                String toLaneEdgeID = Lane.getEdgeID(toLaneID);

                // Wenn die Zielspur auf dem nächsten Edge liegt
                if (toLaneEdgeID.equals(nextEdgeID)) {
                    // Aktualisiere die aktuelle Spur-ID
                    currentLaneID = toLaneID;

                    // Länge der nächsten Spur abrufen
                    Double nextLaneLength = Lane.getLength(currentLaneID);

                    // Aktualisiere die Distanz bis zum Spurende
                    distanceToLaneEnd += nextLaneLength;
                    foundNextLane = true;
                    break;
                }
            }

            if (!foundNextLane) {
                // Keine weitere Verbindung entlang der Route gefunden; Spur endet hier
                break;
            }
        }

        //Wenn es sich um die letzte Edge der gesamten route handelt distance = MaxValue damit auto weiter fährt und entfernt wird
        if(routeIndex == route.size()-1) {
            distanceToLaneEnd = Double.MAX_VALUE;
        }


        vehicle.setLane(lane);
        vehicle.setDistanceToLaneEnd(distanceToLaneEnd);
        vehicle.setNumberOfLanes(numberOfLanes);
    }
}
