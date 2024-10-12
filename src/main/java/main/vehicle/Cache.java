package main.vehicle;

import main.analytics.Analyser;
import main.flocking.Flocking;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.*;

import java.util.*;

public class Cache {
    public static HashMap<String, SimVehicle> vehicles = new HashMap<>();
    public static Map<String, List<SimVehicle>> vehicleGrid = new HashMap<>();
    public static List<MutablePair<TraCICollision,List<SimVehicle>>> collisions = new ArrayList<>();
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
            //Add new vehicle to Cache
            if(!Cache.vehicles.containsKey(vehicleId)) Cache.vehicles.put(vehicleId, new SimVehicle(vehicleId));
            SimVehicle vehicle = Cache.vehicles.get(vehicleId);

            //Get vehicle position and add to Vehicle-Grid for performant Position based search
            TraCIPosition position = Vehicle.getPosition(vehicleId);
            vehicle.setPosition(new MutablePair<>(position.getX(),position.getY()));
            Cache.addVehicleToVehicleGrid(vehicle);

            //Set vehicle speeds
            double currentSpeed = Vehicle.getSpeed(vehicleId);
            vehicle.setCurrentSpeed(currentSpeed);
            vehicle.setTargetSpeed(currentSpeed);
            //vehicle.setMaxVehicleSpeed(Vehicle.getMaxSpeed(vehicleId));
            vehicle.setMaxRoadSpeed(roadSpeeds.get(Vehicle.getRoadID(vehicleId)));

            //Set vehicle leader for separation
            StringDoublePair leaderWithDistance = Vehicle.getLeader(vehicleId);
            double distance = leaderWithDistance.getSecond();
            if(distance != -1) vehicle.setLeaderWithDistance(new MutablePair<>(Cache.vehicles.get(leaderWithDistance.getFirst()),distance));
            else vehicle.setLeaderWithDistance(null);

            //Set vehicle lane information
            getLaneInformation(vehicle);
        });

        //Remove collided Vehicles
        Simulation.getCollisions().forEach(traCICollision -> {
            Cache.collisions.add(new MutablePair<>(traCICollision,List.of(Cache.vehicles.get(traCICollision.getCollider()),Cache.vehicles.get(traCICollision.getVictim()))));
            Cache.removeVehicle(Cache.vehicles.get(traCICollision.getCollider()), VehicleState.COLLIDED);
            Cache.removeVehicle(Cache.vehicles.get(traCICollision.getVictim()), VehicleState.COLLIDED);
        });

        //Remove finished Vehicles
        Cache.vehicles.values().removeIf(vehicle->{
            if(!vehicleIds.contains(vehicle.getVehicleId())) return setVehicleRemoved(vehicle, VehicleState.FINISHED);
            return false;
        });

        vehicles.values().forEach(vehicle -> vehicle.setLaneUtilization(getLaneUtilization(vehicle)));
    }

    private static HashMap<Integer, Integer> getLaneUtilization(SimVehicle vehicle) {
        HashMap<Integer, Integer> laneUtilization = new HashMap<>();
        List<MutablePair<SimVehicle,Double>> neighbours = Cache.getNeighbors(vehicle.getVehicleId(), Flocking.COHESION_NEIGHBOUR_RADIUS);
        neighbours.stream().map(MutablePair::getLeft).forEach(neighbour-> laneUtilization.put(neighbour.getLane(),laneUtilization.getOrDefault(neighbour.getLane(),0) + 1));
        for(int i = 0; i< vehicle.getNumberOfLanes(); i++) laneUtilization.putIfAbsent(i,0);
        return laneUtilization;
    }

    public static void removeVehicle(SimVehicle vehicle,VehicleState vehicleState){
        if(Cache.vehicles.values().remove(vehicle)) setVehicleRemoved(vehicle,vehicleState);
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

    public static void addVehicleToVehicleGrid(SimVehicle vehicle) {
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

    public static void getLaneInformation(SimVehicle vehicle) {
        HashMap<Integer, Double> laneDistances = new HashMap<>();
        String vehicleId = vehicle.getVehicleId();

        // Aktuellen Edge (Straßenabschnitt) des Fahrzeugs abrufen
        String edgeID = Vehicle.getRoadID(vehicleId);

        // Anzahl der Spuren auf dem aktuellen Edge abrufen
        Integer numLanes = Edge.getLaneNumber(edgeID);

        // Route des Fahrzeugs abrufen
        List<String> route = Vehicle.getRoute(vehicleId).stream().toList();

        // Aktueller Index des Edges in der Route
        int routeIndex = Vehicle.getRouteIndex(vehicleId);
        vehicle.setRouteIndex(routeIndex);

        // Aktuelle Position des Fahrzeugs auf der Spur abrufen
        Double vehicleLanePosition = Vehicle.getLanePosition(vehicleId);

        // Schleife über alle Spuren (Lanes) auf dem aktuellen Edge
        for (int laneIndex = 0; laneIndex < numLanes; laneIndex++) {
            String laneID = edgeID + "_" + laneIndex;

            // Verwende die aktuelle Position des Fahrzeugs für alle Spuren
            Double lanePosition = vehicleLanePosition;

            // Länge der aktuellen Spur abrufen
            Double laneLength = Lane.getLength(laneID);

            // Berechnung der verbleibenden Distanz auf der aktuellen Spur
            double distanceToLaneEnd = laneLength - lanePosition;

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

            // Wenn es sich um die letzte Edge der gesamten Route handelt, setze die Distanz auf MaxValue
            if (routeIndex == route.size() - 1) {
                distanceToLaneEnd = Double.MAX_VALUE;
            }

            // Füge das Paar (LaneIndex, Distance) zur HashMap hinzu
            laneDistances.put(laneIndex, distanceToLaneEnd);
        }

        vehicle.setPreviousLane(vehicle.getLane());
        vehicle.setLane(Vehicle.getLaneIndex(vehicleId));
        vehicle.setNumberOfLanes(numLanes);
        vehicle.setDistancesToLaneEnd(laneDistances);
    }
}
