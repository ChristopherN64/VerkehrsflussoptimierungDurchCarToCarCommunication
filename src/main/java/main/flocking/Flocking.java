package main.flocking;

import main.Main;
import main.consensus.Consensus;
import main.vehicle.Cache;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleState;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.StringDoublePair;
import org.eclipse.sumo.libtraci.StringDoublePairVector;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.List;
import java.util.OptionalDouble;

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
    public static int ALIGNMENT_NEIGHBOUR_RADIUS = 120;
    public static double seperationPercent1 = 0.6;
    public static double seperationPercent2 = 0.6;


    //Konstanten für Cohesion
    public static int MAX_DISTANCE_TO_LANE_END = 250;



    public static void performFlocking(SimVehicle vehicle) {
        if (vehicle.isTraffic()) {
            String vehicleId = vehicle.getVehicleId();
            calculateVehicleSimulationParams(vehicle);
            //Sumo steuerung deaktivieren
            deactivateSumoVehicleControl(vehicleId);



            // Separation durchführen
            double newVehicleSpeedFromSeparation = applySeparation(vehicle);

            // Alignment durchführen
            double newVehicleSpeedFromAlignment = applyAlignment(vehicle);
            if(newVehicleSpeedFromAlignment==-1) newVehicleSpeedFromAlignment = newVehicleSpeedFromSeparation;

            // Cohesion durchführen (Spurwechsel)
            double newVehicleSpeedFromCohesion = applyCohesion(vehicle);
            if(newVehicleSpeedFromCohesion==-1) newVehicleSpeedFromCohesion = newVehicleSpeedFromSeparation;



            // Verrechnung
            double newTargetSpeed = newVehicleSpeedFromSeparation;
            //Alignment beschleunigt
            if(newVehicleSpeedFromSeparation < newVehicleSpeedFromAlignment){
                if(vehicle.getVehicleState() == VehicleState.OUT_OF_DISTANCE) {
                    newTargetSpeed = (newVehicleSpeedFromSeparation * seperationPercent1 + newVehicleSpeedFromAlignment * (1 - seperationPercent1));
                }
            }
            else {
                newTargetSpeed = (newVehicleSpeedFromSeparation * seperationPercent2 + newVehicleSpeedFromAlignment * (1 - seperationPercent2));
            }



            //Berechnete neue Geschwindigkeit unter berücksichtigungen der physikalischen möglichkeiten und MaxSpeed anwenden
            applyNewSpeed(vehicle,newTargetSpeed,emergencyBrakingNeeded);
        }
    }

    private static double applyCohesion(SimVehicle vehicle) {
        String vehicleId = vehicle.getVehicleId();
        double distanceToLaneEnd = vehicle.getDistanceToLaneEnd();
        int targetLane = -1;

        vehicle.setLaneChangeNeeded(false);
        Vehicle.setLaneChangeMode(vehicleId, 0);

        //TODO bedingung für ende der gesamten Fahrbahn
        if (distanceToLaneEnd < MAX_DISTANCE_TO_LANE_END && vehicle.getRouteIndex() > 0){
            vehicle.setLaneChangeNeeded(true);

            //0 = look Left | 1 = look Right
            int neighbourDirectionLeftRight;
            int currentLane = vehicle.getLane();
            //Die rechte Spur endet
            if(currentLane == 0) {
                targetLane = 1;
                neighbourDirectionLeftRight = 0;
            }
            //Die linke Spur endet
            else {
                targetLane = currentLane - 1;
                neighbourDirectionLeftRight = 1;
            }


            MutablePair<SimVehicle,Double> follower = getLaneChangRelevantNeighbours(vehicle,neighbourDirectionLeftRight,0);
            MutablePair<SimVehicle,Double> leader = getLaneChangRelevantNeighbours(vehicle,neighbourDirectionLeftRight,1);
            System.out.println();

            Vehicle.setLaneChangeMode(vehicleId,-1);
            Vehicle.changeLane(vehicleId,targetLane,1000);
        }
        return -1;
    }

    private static MutablePair<SimVehicle, Double> getLaneChangRelevantNeighbours(SimVehicle simVehicle, int neighbourDirectionLeftRight, int neighbourDirectionFollowerLeader) {
        int bits = (0) | (neighbourDirectionFollowerLeader << 1) | neighbourDirectionLeftRight;
        StringDoublePairVector neighboursVector = Vehicle.getNeighbors(simVehicle.getVehicleId(),bits);
        if(neighboursVector.isEmpty()) return null;
        else return new MutablePair<>(Cache.vehicles.get(neighboursVector.getFirst().getFirst()),neighboursVector.getFirst().getSecond());
    }


    private static double applyAlignment(SimVehicle vehicle) {
        List<MutablePair<SimVehicle,Double>> neighbours = Cache.getNeighbors(vehicle.getVehicleId(),ALIGNMENT_NEIGHBOUR_RADIUS);
        OptionalDouble avgNeighbourSpeed = neighbours.stream().mapToDouble(mp->mp.getLeft().getTargetSpeed()).average();
        if(avgNeighbourSpeed.isPresent()) return avgNeighbourSpeed.getAsDouble();
        return -1;
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
