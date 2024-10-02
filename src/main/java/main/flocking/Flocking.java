package main.flocking;

import main.vehicle.Cache;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleState;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.Edge;
import org.eclipse.sumo.libtraci.Lane;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.List;

public class Flocking {

    // Konstanten für die Simulation
    private static final double MAX_DISTANCE = 13; // Gewünschter Abstand in Metern
    private static final double MIN_DISTANCE = 10; // Gewünschter Abstand in Metern
    private static final double MAX_SPEED_DIFF_TO_LEADER = 1.05; // Gewünschter Abstand in Metern
    private static final double MAX_SPEED_INCREMENT = 0.5; // Maximale Geschwindigkeitsänderung pro Zeitschritt in m/s
    private static final double MAX_SPEED_DECREMENT = 4.5; // Maximale Geschwindigkeitsreduktion pro Zeitschritt in m/s
    private static final double MAX_EMERGENCY_SPEED_DECREMENT = 12; // Maximale Geschwindigkeitsreduktion pro Zeitschritt in m/s


    public static void performFlocking(SimVehicle vehicle) {
        if (vehicle.isTraffic()) {
            String vehicleId = vehicle.getVehicleId();
            deactivateSumoVehicleControl(vehicleId);

            // Separation durchführen
            applySeparation(vehicle);

            // Alignment durchführen
            //applyAlignment(vehicle);

            // Geschwindigkeit aktualisieren
            Vehicle.setSpeed(vehicleId, vehicle.getTargetSpeed());

            // Cohesion durchführen (Spurwechsel)
            //applyCohesion();
            //checkAndChangeLane(vehicle.getVehicleId(), 22);
        }
    }

    private static void applySeparation(SimVehicle vehicle) {
        if (vehicle.getVehicleId().startsWith("normal_1.0")) {
            System.out.println();
        }
        double ownSpeed = vehicle.getCurrentSpeed();
        double newOwnSpeed = ownSpeed;

        if (vehicle.getLeaderWithDistance() != null &&
                vehicle.getLeaderWithDistance().getLeft() != null &&
                vehicle.getLeaderWithDistance().getRight() != null) {

            SimVehicle leadingVehicle = vehicle.getLeaderWithDistance().getLeft();
            double distanceToLeadingVehicle = vehicle.getLeaderWithDistance().getRight();
            double leadingVehicleSpeed = leadingVehicle.getTargetSpeed();

            if (distanceToLeadingVehicle < MIN_DISTANCE) {
                // Zu nah am Vorderfahrzeug, reduziere die Geschwindigkeit

                // Reduziere die Geschwindigkeit, aber nicht mehr als MAX_SPEED_DECREMENT
                double speedReduction = Math.min(ownSpeed * MAX_SPEED_DIFF_TO_LEADER - leadingVehicleSpeed, MAX_EMERGENCY_SPEED_DECREMENT);
                newOwnSpeed = ownSpeed - speedReduction;

            } else if (distanceToLeadingVehicle > MAX_DISTANCE) {
                // Zu weit vom Vorderfahrzeug entfernt, erhöhe die Geschwindigkeit

                double maxAllowedSpeed = leadingVehicleSpeed * MAX_SPEED_DIFF_TO_LEADER;

                // Begrenze die Geschwindigkeitsänderung pro Zeitschritt
                double speedIncrease = Math.min(Double.max(maxAllowedSpeed - ownSpeed, -MAX_SPEED_DECREMENT), MAX_SPEED_INCREMENT);
                newOwnSpeed = ownSpeed + speedIncrease;

                // Stelle sicher, dass die eigene Geschwindigkeit nicht über die DESIRED_SPEED geht
                newOwnSpeed = Math.min(newOwnSpeed, vehicle.getDesiredSpeed());

            }
            else {
                newOwnSpeed = leadingVehicleSpeed;
            }
        } else {
            // Kein Vorderfahrzeug: Strebe die gewünschte Geschwindigkeit an, erhöhe Geschwindigkeit nicht abrupt
            double speedIncrease = Math.min(vehicle.getDesiredSpeed() - ownSpeed, MAX_SPEED_INCREMENT);
            newOwnSpeed = ownSpeed + speedIncrease;
        }

        if(ownSpeed-newOwnSpeed > MAX_SPEED_DECREMENT) {
            vehicle.emergencyBrake();
        }

        vehicle.setTargetSpeed(newOwnSpeed);
    }

    private static void deactivateSumoVehicleControl(String vehicleId) {
        //Bei normalem Speed mode
        //Speed beachtet Decel Acel UND minGap
        //Heist geschwindigkeit wird immer decel Acel und Mingap angepasst und somit unsauber
        //Bei Speed Mode 0
        //Sowohl mingap als auch decel Accel wird ignoriert

        Vehicle.setLaneChangeMode(vehicleId, 0);
        Vehicle.setSpeedMode(vehicleId, 0);
        Vehicle.setParameter(vehicleId, "sigma", "0");
        Vehicle.setTau(vehicleId, 0.1);
    }


    // Funktion, die die Spur wechselt, wenn nötig
    public static void checkAndChangeLane(String vehicleID, double distanceThreshold) {

        // Hole die aktuelle Spur-ID und die Länge der Spur
        String currentLaneID = (String) Vehicle.getLaneID(vehicleID);
        double laneLength = (double) Lane.getLength(currentLaneID);

        // Hole die aktuelle Position des Fahrzeugs auf der Spur
        double lanePosition = (double) Vehicle.getLanePosition(vehicleID);

        // Berechne die verbleibende Distanz bis zum Spurende
        double distanceUntilLaneEnd = laneLength - lanePosition;

        // Hole die aktuelle Kante und die Route des Fahrzeugs
        String currentEdgeID = (String) Vehicle.getRoadID(vehicleID);
        List<String> route = Vehicle.getRoute(vehicleID).stream().toList();

        // Bestimme die Indexposition der aktuellen Kante
        int currentEdgeIndex = -1;
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).equals(currentEdgeID)) {
                currentEdgeIndex = i;
                break;
            }
        }

        // Bestimme die nächste Kante, wenn verfügbar
        String nextEdgeID = null;
        if (currentEdgeIndex + 1 < route.size()) {
            nextEdgeID = route.get(currentEdgeIndex + 1);
        }

        // Überprüfen, ob die Spur gewechselt werden muss
        if (distanceUntilLaneEnd <= distanceThreshold && nextEdgeID != null) {
            // Hole die aktuelle Spur-Nummer
            String[] laneIDParts = currentLaneID.split("_");
            int currentLaneIndex = Integer.parseInt(laneIDParts[1]);

            // Anzahl der Spuren auf der nächsten Kante
            int nextLaneCount = (int) Edge.getLaneNumber(nextEdgeID);

            // Überprüfen, ob die aktuelle Spur auf der nächsten Kante existiert
            if (currentLaneIndex >= nextLaneCount) {
                // Wechsle die Spur zur nächst verfügbaren Spur (nach rechts)
                if (currentLaneIndex > 0) {
                    int newLaneIndex = currentLaneIndex - 1; // Wechsel zur rechten Spur
                    //TODO Lanechange sicher implementieren
                   // Vehicle.changeLane(vehicleID, newLaneIndex, 100.0);
                    System.out.println("Vehicle " + vehicleID + " changed to lane " + newLaneIndex + ".");
                } else {
                    System.out.println("Vehicle " + vehicleID + " is on the rightmost lane and cannot change right.");
                }
            } else {
                System.out.println("Lane " + currentLaneIndex + " continues on the next edge '" + nextEdgeID + "'. No lane change needed.");
            }
        }

    }

}
