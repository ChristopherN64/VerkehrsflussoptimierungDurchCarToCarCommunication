package main.flocking;

import main.consensus.Consensus;
import main.vehicle.SimVehicle;
import org.eclipse.sumo.libtraci.Vehicle;

public class Flocking {

    //Physikalische Konstanten
    private static final double MAX_SPEED_INCREMENT = 2.5; // Maximale Geschwindigkeitssteigerung pro Zeitschritt in m/s
    private static final double MAX_SPEED_DECREMENT = 4.5; // Maximale Geschwindigkeitsreduktion pro Zeitschritt in m/s
    private static final double MAX_EMERGENCY_SPEED_DECREMENT = 12; // Maximale Geschwindigkeitsreduktion pro Zeitschritt bei Gefahrenbremsung in m/s

    // Konstanten für die Simulation

    private static final double MAX_MIN_DISTANCE_DIFF = 1.2; // Gewünschter Abstand in Metern
    private static final double MAX_SPEED_DIFF_TO_LEADER = 1.05; // Gewünschter Abstand in Metern
    protected static final double MAX_DESIRED_SPEED_OFFSET = 1.1;

    private static double maxDistance; // Gewünschter Abstand in Metern
    private static double minDistance; // Minimaler Sicherheitsabstand Abstand in Metern
    private static boolean emergencyBrakingNeeded;
    private static double maxFlockingSpeed;


    public static void performFlocking(SimVehicle vehicle) {
        if (vehicle.isTraffic()) {
            double newVehicleSpeed;
            String vehicleId = vehicle.getVehicleId();

            calculateVehicleSimulationParams(vehicle);

            //Sumo steuerung deaktivieren
            deactivateSumoVehicleControl(vehicleId);

            // Separation durchführen
            newVehicleSpeed = applySeparation(vehicle);

            // Alignment durchführen
            //applyAlignment(vehicle);

            // Cohesion durchführen (Spurwechsel)
            //applyCohesion();

            //Berechnete neue Geschwindigkeit unter berücksichtigungen der physikalischen möglichkeiten und MaxSpeed anwenden
            applyNewSpeed(vehicle,newVehicleSpeed,emergencyBrakingNeeded);
        }
    }

    private static double applySeparation(SimVehicle vehicle) {
        double ownSpeed = vehicle.getCurrentSpeed();

        if (vehicle.getLeaderWithDistance() != null &&
                vehicle.getLeaderWithDistance().getLeft() != null &&
                vehicle.getLeaderWithDistance().getRight() != null) {

            SimVehicle leadingVehicle = vehicle.getLeaderWithDistance().getLeft();
            double distanceToLeadingVehicle = vehicle.getLeaderWithDistance().getRight();
            double leadingVehicleSpeed = leadingVehicle.getTargetSpeed();

            //Mindestabstand unterschritten -> Bremsen - auch Gefahrenbremsung
            if (distanceToLeadingVehicle < minDistance) {
                // Reduziere die Geschwindigkeit auf maximal die des Vorrausfahrenden
                double distance_diff = minDistance - distanceToLeadingVehicle;
                distance_diff -= (leadingVehicleSpeed - ownSpeed);
                emergencyBrakingNeeded = true;
                return ownSpeed - distance_diff;
            }
            //Maximalabstand überschritten -> beschleunige zum aufholen aber maximal MAX_SPEED_DIFF_TO_LEADER Prozent schnelle als der vorrausfahrende
            if (distanceToLeadingVehicle > maxDistance) {
                // Zu weit vom Vorderfahrzeug entfernt, erhöhe die Geschwindigkeit
                double targetSpeedToCatchLeader = (leadingVehicleSpeed + 1.5) * MAX_SPEED_DIFF_TO_LEADER;
                return targetSpeedToCatchLeader;
            }
            else {
                return leadingVehicleSpeed;
            }
        } else {
            double newOwnSpeed = maxFlockingSpeed;
            if(vehicle.getDistanceToLaneEnd() < 30) newOwnSpeed = 0;
            return newOwnSpeed;
        }
    }

    public static void applyNewSpeed(SimVehicle vehicle, double newVehicleSpeed, boolean emergencyBreakAllowed){
        double acceleration = newVehicleSpeed - vehicle.getCurrentSpeed();

        //Calculate acceleration (or deceleration) (unter berücksichtigung der Physikalisch möglichen beschleunigung / Verzögerung)
        if(acceleration<0) {
            if(emergencyBreakAllowed) acceleration = Double.max(acceleration,-MAX_EMERGENCY_SPEED_DECREMENT);
            else acceleration = Double.max(acceleration,-MAX_SPEED_DECREMENT);
        } else acceleration = Double.min(acceleration,MAX_SPEED_INCREMENT);

        //Calculate newSpeed (unter berücksichtigung der Maximalen geschwindigkeit + MAX_DESIRED_SPEED_OFFSET und nicht negativ)
        double newTargetSpeed = vehicle.getCurrentSpeed() + acceleration;
        if(newTargetSpeed < 0) newTargetSpeed = 0;
        if(newTargetSpeed > vehicle.getDesiredSpeed() * MAX_DESIRED_SPEED_OFFSET) newTargetSpeed = vehicle.getDesiredSpeed() * MAX_DESIRED_SPEED_OFFSET;
        if(newTargetSpeed > maxFlockingSpeed) newTargetSpeed = maxFlockingSpeed;


        //EmergencyBrankings loggen
        if(vehicle.getCurrentSpeed() - newTargetSpeed > MAX_SPEED_DECREMENT && vehicle.getRouteIndex()!=0) vehicle.emergencyBrake();

        vehicle.setTargetSpeed(newTargetSpeed);
        Vehicle.setSpeed(vehicle.getVehicleId(), newTargetSpeed);
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

    private static void calculateVehicleSimulationParams(SimVehicle vehicle){
        emergencyBrakingNeeded = false;
        //das Flocking strebt maximal eine leicht höhere Geschwindigkeit an ab der es als Stau zählt
        maxFlockingSpeed = Consensus.MINIMUM_SPEED_PERCENTAGE_WITHOUT_TRAFFIC * vehicle.getDesiredSpeed() * 1.1;
        minDistance = (vehicle.getCurrentSpeed() * 3.6) / 3;
        if(minDistance < 10) minDistance = 10;
        maxDistance = minDistance * MAX_MIN_DISTANCE_DIFF;
    }

}
