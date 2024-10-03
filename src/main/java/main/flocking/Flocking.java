package main.flocking;

import main.consensus.Consensus;
import main.vehicle.Cache;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleState;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.List;

public class Flocking {

    //Physikalische Konstanten
    private static final double MAX_SPEED_INCREMENT = 2.5; // Maximale Geschwindigkeitssteigerung pro Zeitschritt in m/s
    private static final double MAX_SPEED_DECREMENT = 4.5; // Maximale Geschwindigkeitsreduktion pro Zeitschritt in m/s
    private static final double MAX_EMERGENCY_SPEED_DECREMENT = 12; // Maximale Geschwindigkeitsreduktion pro Zeitschritt bei Gefahrenbremsung in m/s

    // Konstanten für die Separation
    private static final double MAX_MIN_DISTANCE_DIFF = 1.5; // Gewünschter Abstand in Metern
    private static final double MIN_SPEED_DIFF_UNDER_DISTANCE = 0.95; // Gewünschter Abstand in Metern
    private static final double MAX_SPEED_DIFF_TO_LEADER = 1.05; // Gewünschter Abstand in Metern
    protected static final double MAX_DESIRED_SPEED_OFFSET = 1.1;
    private static double maxDistance; // Gewünschter Abstand in Metern
    private static double minDistance; // Minimaler Sicherheitsabstand Abstand in Metern
    private static boolean emergencyBrakingNeeded;
    private static double maxFlockingSpeed;


    //Konstanten für Alignment
    private static final int ALIGNMENT_NEIGHBOUR_RADIUS = 50;




    public static void performFlocking(SimVehicle vehicle) {
        if (vehicle.isTraffic()) {
            String vehicleId = vehicle.getVehicleId();
            calculateVehicleSimulationParams(vehicle);
            //Sumo steuerung deaktivieren
            deactivateSumoVehicleControl(vehicleId);



            // Separation durchführen
            double newVehicleSpeedFromSeparation;
            newVehicleSpeedFromSeparation = applySeparation(vehicle);

            // Alignment durchführen
            double newVehicleSpeedFromAlignment;
            //newVehicleSpeedFromAlignment = applyAlignment(vehicle);

            // Cohesion durchführen (Spurwechsel)
            //applyCohesion();



            //Berechnete neue Geschwindigkeit unter berücksichtigungen der physikalischen möglichkeiten und MaxSpeed anwenden
            applyNewSpeed(vehicle,newVehicleSpeedFromSeparation,emergencyBrakingNeeded);
        }
    }


    private static double applyAlignment(SimVehicle vehicle) {
        double currentSpeed = vehicle.getCurrentSpeed();
        List<MutablePair<SimVehicle,Double>> neighbours = Cache.getNeighbors(vehicle.getVehicleId(),ALIGNMENT_NEIGHBOUR_RADIUS);
        double avgNeighbourSpeed = neighbours.stream().mapToDouble(mp->mp.getLeft().getTargetSpeed()).average().getAsDouble();
        double newTargetSpeed = avgNeighbourSpeed;
        return newTargetSpeed;
    }


    //Sorgt für den idealen Abstand zwischen dem Fahrzeug und dem vorausfahrenden und verhindert Kollisionen
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
                // Reduziere die Geschwindigkeit auf maximal 95% des Vorhausfahrenden
                emergencyBrakingNeeded = true;
                vehicle.setVehicleState(VehicleState.UNDER_DISTANCE);
                return Double.min(leadingVehicleSpeed * MIN_SPEED_DIFF_UNDER_DISTANCE,ownSpeed);
            }
            //Maximalabstand überschritten -> beschleunige zum aufholen aber maximal MAX_SPEED_DIFF_TO_LEADER Prozent schnelle als der vorrausfahrende
            if (distanceToLeadingVehicle > maxDistance) {
                // Zu weit vom Vorderfahrzeug entfernt, erhöhe die Geschwindigkeit
                vehicle.setVehicleState(VehicleState.OUT_OF_DISTANCE);
                return (leadingVehicleSpeed + 1.5) * MAX_SPEED_DIFF_TO_LEADER;
            }
            else {
                vehicle.setVehicleState(VehicleState.IN_DISTANCE);
                return leadingVehicleSpeed;
            }
        } else {
            vehicle.setVehicleState(VehicleState.NO_LEADER);
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
        minDistance = (vehicle.getCurrentSpeed() * 3.6) / 4;
        if(minDistance < 10) minDistance = 10;
        maxDistance = minDistance * MAX_MIN_DISTANCE_DIFF;
    }

}
