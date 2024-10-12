package main.flocking;

import main.Main;
import main.consensus.Consensus;
import main.vehicle.Cache;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleState;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.StringDoublePairVector;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.HashMap;
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
    public static double seperationPercentageWhenAligmentAcellarate = 0.6;
    public static double seperationPercentageWhenAligmentDecellarate = 0.6;

    //Konstanten für Cohesion
    public static int MAX_DISTANCE_TO_LANE_END = 500;
    public static double MAX_LANE_CHANGE_DECELERATION = 0.90;
    public static double MAX_LANE_CHANGE_ACCELERATION = 1.15;
    public static int COHESION_NEIGHBOUR_RADIUS = 400;
    public static double COHESION_MINIMUM_UTILIZATION_OFFSET_ON_NEW_LANE = 1.6;
    public static int COHESION_LANE_CHANGE_COOLDOWN = 20;


    public static void performFlocking(SimVehicle vehicle) {
        calculateVehicleSimulationParams(vehicle);
        //Bei endender Spur immer Flocking
        if(!vehicle.isTraffic() && laneIsEnding(vehicle,vehicle.getLane()) && vehicle.getRouteIndex() > 0) {
            vehicle.setTraffic(true);
        }
        if(vehicle.isTraffic()) {
            double newTargetSpeed = vehicle.getCurrentSpeed();
            String vehicleId = vehicle.getVehicleId();

            //Sumo (normale Fahrer) Steuerung für Fahrzeug deaktivieren
            deactivateSumoVehicleControl(vehicleId);

            // Separation durchführen
            double newVehicleSpeedFromSeparation = applySeparation(vehicle);



            // Alignment durchführen
            double newVehicleSpeedFromAlignment = applyAlignment(vehicle);
            if(newVehicleSpeedFromAlignment==-1) newVehicleSpeedFromAlignment = newVehicleSpeedFromSeparation;

            // Verrechnung von Separation und Alignment
            newTargetSpeed = newVehicleSpeedFromSeparation;
            //Alignment beschleunigt, nur wenn Fahrzeug OUT_OF_DISTANCE zum Leader ist
            if(newVehicleSpeedFromSeparation < newVehicleSpeedFromAlignment && vehicle.getVehicleState() == VehicleState.OUT_OF_DISTANCE){
                newTargetSpeed = (newVehicleSpeedFromSeparation * seperationPercentageWhenAligmentAcellarate + newVehicleSpeedFromAlignment * (1 - seperationPercentageWhenAligmentAcellarate));
            }
            //Alignment bremst.
            else if(newVehicleSpeedFromSeparation > newVehicleSpeedFromAlignment) newTargetSpeed = (newVehicleSpeedFromSeparation * seperationPercentageWhenAligmentDecellarate + newVehicleSpeedFromAlignment * (1 - seperationPercentageWhenAligmentDecellarate));



            // Cohesion durchführen (Spurwechsel)
            double newVehicleSpeedFromCohesion = applyCohesion(vehicle, newTargetSpeed);
            if(newVehicleSpeedFromCohesion != -1 && (newVehicleSpeedFromCohesion < newTargetSpeed || vehicle.getVehicleState() != VehicleState.UNDER_DISTANCE)) {
                newTargetSpeed = newVehicleSpeedFromCohesion;
            }


            //Neue Geschwindigkeit unter berücksichtigungen der physikalischen Möglichkeiten und MaxSpeed anwenden
            applyNewSpeed(vehicle,newTargetSpeed,emergencyBrakingNeeded);
        }
    }

    //Sort für den geordneten Spurwechsel bei Spurende und zur idealen und gleichmäßigen verteilung der Fahrzeuge auf alle Spuren
    private static double applyCohesion(SimVehicle vehicle,double newTargetSpeedFromPreviousFunctions) {
        String vehicleId = vehicle.getVehicleId();
        double distanceToEndOfCurrentLane = vehicle.getDistanceToEndOfCurrentLane();
        int targetLane = -1;

        //Remove all Leader / Follower Links from last Step
        if(vehicle.isLaneChangeNeeded()) {
            vehicle.setLaneChangeNeeded(false);
            if(vehicle.getFollowerOnTargetLane()!=null) {
                vehicle.getFollowerOnTargetLane().getLeft().setLeaderOnEndingLane(null);
                vehicle.setFollowerOnTargetLane(null);
            }
            if(vehicle.getLeaderOnTargetLane()!=null) {
                vehicle.getLeaderOnTargetLane().getLeft().setFollowerOnEndingLane(null);
                vehicle.setLeaderOnTargetLane(null);
            }
        }
        deactivateSumoVehicleControl(vehicleId);


        //Calculate if lane change ist needed
        int currentLane = vehicle.getLane();

        //Fahrzeug muss selber die Spur wechseln da seine Spur endet
        //Berücksichtigt COHESION_LANE_CHANGE_COOLDOWN nicht
        if(laneIsEnding(vehicle,vehicle.getLane()) && vehicle.getRouteIndex() > 0){
            vehicle.setLaneChangeNeeded(true);
            //Die rechte Spur endet
            if(currentLane == 0) targetLane = 1;
            //Die linke Spur endet
            else targetLane = currentLane - 1;
        }

        //Fahrzeug sollte selber die wechseln da eine andere Spur deutlich weniger ausgelastet ist
        //Berücksichtigt COHESION_LANE_CHANGE_COOLDOWN
        //Im bereich in dem eine Spur endet ist der Cooldown halbiert und die Auslastungsdifferenz muss kleiner sein
        else {
            double distanceToNearestLaneEnd = vehicle.getDistancesToLaneEnd().values().stream().min(Double::compareTo).orElse(Double.MAX_VALUE);
            int indexOfEndingLane = -1;
            double minimumUtilisationOffsetOnNewLane = COHESION_MINIMUM_UTILIZATION_OFFSET_ON_NEW_LANE;
            if(distanceToNearestLaneEnd < MAX_DISTANCE_TO_LANE_END && vehicle.getDistancesToLaneEnd().values().stream().distinct().count() != 1){
                indexOfEndingLane = 2;
                minimumUtilisationOffsetOnNewLane = 1.4;
                if(Main.step - vehicle.getStepOfLastLaneChange() < COHESION_LANE_CHANGE_COOLDOWN / 2) {
                    vehicle.setStepOfLastLaneChange(Main.step - (COHESION_LANE_CHANGE_COOLDOWN / 2));
                }
            }
            targetLane = getNewTargetLaneByLaneUtilization(vehicle,indexOfEndingLane,minimumUtilisationOffsetOnNewLane);
            if(targetLane != vehicle.getLane() && (Main.step - vehicle.getStepOfLastLaneChange() > COHESION_LANE_CHANGE_COOLDOWN)) {
                vehicle.setLaneChangeNeeded(true);
            }
        }

        //Spurwechsel wenn nötig durchführen
        if (vehicle.isLaneChangeNeeded()){
            //0 = look Left | 1 = look Right
            int neighbourDirectionLeftRight = 0;
            if(targetLane <= vehicle.getLane()) neighbourDirectionLeftRight = 1;

            MutablePair<SimVehicle,Double> followerOnTargetLane = getLaneChangRelevantNeighbours(vehicle,neighbourDirectionLeftRight,0);
            if(followerOnTargetLane!=null) {
                vehicle.setFollowerOnTargetLane(followerOnTargetLane);
                Cache.vehicles.get(followerOnTargetLane.getLeft().getVehicleId()).setLeaderOnEndingLane(new MutablePair<>(vehicle,-followerOnTargetLane.getRight()));
            }

            MutablePair<SimVehicle,Double> leaderOnTargetLane = getLaneChangRelevantNeighbours(vehicle,neighbourDirectionLeftRight,1);
            if(leaderOnTargetLane!=null) {
                vehicle.setLeaderOnTargetLane(leaderOnTargetLane);
                Cache.vehicles.get(leaderOnTargetLane.getLeft().getVehicleId()).setFollowerOnEndingLane(new MutablePair<>(vehicle,-leaderOnTargetLane.getRight()));
            }

            //Aktiviere Sumo-Spurwechsel bis das Fahrzeug die target Spur erreicht hat
            Vehicle.setLaneChangeMode(vehicleId,-1);
            try {
                Vehicle.setParameter(vehicleId, "laneChangeModel", "LC2013");
                Vehicle.setParameter(vehicleId, "lcStrategic", "1");
                Vehicle.setParameter(vehicleId, "lcCooperative", "1");
                Vehicle.setParameter(vehicleId, "lcSpeedGain", "1");
                Vehicle.setParameter(vehicleId, "lcKeepRight", "0");
                Vehicle.setParameter(vehicleId, "lcAssertive", "0");
                Vehicle.changeLane(vehicleId,targetLane,10);
                vehicle.setStepOfLastLaneChange(Main.step);
            } catch (IllegalArgumentException e){
                System.out.println("Lane Change failed! Target Lane " + targetLane + " current lane: "+vehicle.getLane()+"  number of Lanes: "+vehicle.getNumberOfLanes());
            }

            if(distanceToEndOfCurrentLane < (double) MAX_DISTANCE_TO_LANE_END / 1.5){
                //Fahrzeug bremst wenn jemand auf der Target Spur das einscheren verhindert
                if(vehicle.getLeaderOnTargetLane() != null && vehicle.getLeaderOnTargetLane().getRight() < 5)
                {
                    double leaderTargetSpeed = vehicle.getLeaderOnTargetLane().getLeft().getTargetSpeed();
                    if(leaderTargetSpeed < vehicle.getCurrentSpeed()) {
                        return vehicle.getCurrentSpeed() * 0.85;
                    }
                    else return Double.max(vehicle.getCurrentSpeed() * MAX_LANE_CHANGE_DECELERATION,leaderTargetSpeed * 0.8) ;
                }
                else if(vehicle.getFollowerOnTargetLane() != null && vehicle.getFollowerOnTargetLane().getRight() < 3) {
                    double followerTargetSpeed = vehicle.getFollowerOnTargetLane().getLeft().getTargetSpeed();
                    if(followerTargetSpeed < vehicle.getCurrentSpeed()) {
                        return vehicle.getCurrentSpeed() * 0.85;
                    }
                    else return Double.max(vehicle.getCurrentSpeed() * MAX_LANE_CHANGE_DECELERATION,followerTargetSpeed * 0.8) ;
                }
            }
        }

        //Fahrzeug muss selbst nicht die Spur wechseln, hat aber jemanden hinter sich der hinter ihm einschären möchte und beschleunigt um dies schneller zu ermöglichen
        else if(vehicle.getFollowerOnEndingLane() != null && vehicle.getFollowerOnEndingLane().getRight() > 2  && vehicle.getFollowerOnEndingLane().getRight() > -5){
            return newTargetSpeedFromPreviousFunctions * MAX_LANE_CHANGE_ACCELERATION;
        }
        return newTargetSpeedFromPreviousFunctions;
    }

    private static int getNewTargetLaneByLaneUtilization(SimVehicle vehicle,int indexOfEndingLane,double minimumUtilisationOffsetOnNewLane){
        HashMap<Integer,Integer> laneUtilization = vehicle.getLaneUtilization();
        int utilizationOnCurrentLane = laneUtilization.get(vehicle.getLane());
        //Überprüfe spur eins weiter rechts
        if(checkIfLaneIsBetter(vehicle.getLane() - 1,vehicle,utilizationOnCurrentLane,laneUtilization,indexOfEndingLane,minimumUtilisationOffsetOnNewLane)) return vehicle.getLane() - 1;
        //Überprüfe spur eins weiter links
        if(checkIfLaneIsBetter(vehicle.getLane() + 1 ,vehicle,utilizationOnCurrentLane,laneUtilization,indexOfEndingLane,minimumUtilisationOffsetOnNewLane)) return vehicle.getLane() + 1;
        return vehicle.getLane();
    }

    public static boolean checkIfLaneIsBetter(int potentialLane,SimVehicle vehicle,int utilizationOnCurrentLane,HashMap<Integer,Integer> laneUtilization,int indexOfEndingLane,double minimumUtilisationOffsetOnNewLane){
        return  (potentialLane >= 0 && potentialLane < vehicle.getNumberOfLanes() && potentialLane != indexOfEndingLane
                && (double) utilizationOnCurrentLane / laneUtilization.get(potentialLane) > minimumUtilisationOffsetOnNewLane
                && !laneIsEnding(vehicle,potentialLane));
    }

    public static boolean laneIsEnding(SimVehicle vehicle, int lane){
        return vehicle.getDistancesToLaneEnd().get(lane) < MAX_DISTANCE_TO_LANE_END && vehicle.getDistancesToLaneEnd().values().stream().distinct().count() != 1;
    }

    private static MutablePair<SimVehicle, Double> getLaneChangRelevantNeighbours(SimVehicle simVehicle, int neighbourDirectionLeftRight, int neighbourDirectionFollowerLeader) {
        int bits = (0) | (neighbourDirectionFollowerLeader << 1) | neighbourDirectionLeftRight;
        StringDoublePairVector neighboursVector = Vehicle.getNeighbors(simVehicle.getVehicleId(),bits);
        if(neighboursVector.isEmpty()) return null;
        else return new MutablePair<>(Cache.vehicles.get(neighboursVector.getFirst().getFirst()),neighboursVector.getFirst().getSecond());
    }

    //Sorgt für den Zusammenhalt aller Fahrzeuge (glättet die Geschwindigkeiten)
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
                // Reduziere die Geschwindigkeit auf maximal MIN_SPEED_DIFF_UNDER_DISTANCE Prozent des Vorhausfahrenden
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
            if(vehicle.getDistanceToEndOfCurrentLane() < 30) newOwnSpeed = 0;
            return newOwnSpeed;
        }
    }

    public static void applyNewSpeed(SimVehicle vehicle, double newVehicleSpeed, boolean emergencyBreakAllowed){
        double acceleration = newVehicleSpeed - vehicle.getCurrentSpeed();

        //Calculate acceleration (or deceleration) (unter berücksichtigung der Physikalisch möglichen Beschleunigung / Verzögerung)
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

        //apply new Speed to Vehicle
        vehicle.setTargetSpeed(newTargetSpeed);
        Vehicle.setSpeed(vehicle.getVehicleId(), newTargetSpeed);
    }

    private static void deactivateSumoVehicleControl(String vehicleId) {
        //Kein selbstständiges Spurwechseln
        Vehicle.setLaneChangeMode(vehicleId, -1);
        //Keine selbstständigen Geschwindigkeitsänderungen und kein Abstandshalten
        Vehicle.setSpeedMode(vehicleId, 0);
        Vehicle.setParameter(vehicleId, "sigma", "0");
        Vehicle.setTau(vehicleId, 0.1);
    }

    private static void calculateVehicleSimulationParams(SimVehicle vehicle){
        emergencyBrakingNeeded = false;
        //Das Flocking strebt maximal eine leicht höhere Geschwindigkeit an ab der es als Stau zählt
        maxFlockingSpeed = vehicle.getDesiredSpeed() * 1.1;
        //Minimaler Sicherheitsabstand = viertel der Geschwindigkeit in Km/h aber mindestens 10m
        minDistance = (vehicle.getCurrentSpeed() * 3.6) / 4;
        if(minDistance < 4) minDistance = 4;
        //Mindestabstand ist im bereich in dem eine Spur endet höher (15m)
        if(vehicle.getDistancesToLaneEnd().values().stream().distinct().count() != 1){
            if(vehicle.getLane() > 0 && vehicle.getDistancesToLaneEnd().get(vehicle.getLane()-1) < (double) MAX_DISTANCE_TO_LANE_END) minDistance = 15;
            if(vehicle.getLane() < vehicle.getNumberOfLanes() - 1 && vehicle.getDistancesToLaneEnd().get(vehicle.getLane() + 1) < (double) MAX_DISTANCE_TO_LANE_END) minDistance = 15;
        }
        //Der ideale Abstand ist zwischen Mindestabstand und Mindestabstand * MAX_MIN_DISTANCE_DIFF
        maxDistance = minDistance * MAX_MIN_DISTANCE_DIFF;
    }

}
