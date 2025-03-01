package main.vehicle;

import lombok.Data;
import main.analytics.Analyser;
import main.communication.CarToXMessage;
import main.consensus.Consensus;
import main.*;
import main.flocking.Flocking;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;


@Data
public class SimVehicle {

    //General Informations
    private String vehicleId;
    private MutablePair<Double,Double> position;
    private int ageInSteps;
    private VehicleState vehicleState;
    private LinkedList<CarToXMessage> messageQueue;

    //Speed Informations
    private double currentSpeed;
    private double targetSpeed;
    private double maxVehicleSpeed;
    private double physicalMaxSpeed;
    private double speedFactor;
    private double maxRoadSpeed;

    //Lane / Route Informations
    private int lane;
    private int previousLane;
    private int numberOfLanes;
    private int stepOfLastLaneChange;
    private HashMap<Integer, Double> distancesToLaneEnd;
    private HashMap<Integer, Integer> laneUtilization;
    private int routeIndex;

    //Flocking Informations
    private boolean isTraffic;
    private HashMap<Boolean,Integer> trafficEstimationsSinceLastChange;
    private int flockingActivationStep;
    private int flockingDeactivationStep;
    private MutablePair<SimVehicle,Double> leaderWithDistance;

    //Cohesion Informations
    private boolean laneChangeNeeded;
    private MutablePair<SimVehicle,Double> followerOnTargetLane;
    private MutablePair<SimVehicle,Double> leaderOnTargetLane;
    private MutablePair<SimVehicle,Double> followerOnEndingLane;
    private MutablePair<SimVehicle,Double> leaderOnEndingLane;

    //Analysis Informations
    private int numberOfEmergencyBraking;

    public void simulateStep(){
        reinitNormalVehicleBehavior();
        if(Main.SIMULATE_CONSENSUS)  Consensus.simulateConsensus(this);
        else setTraffic(true);

        if(Main.SIMULATE_FLOCKING) Flocking.performFlocking(this);

        if(!isTraffic) reinitNormalVehicleBehavior();

        clearCarToXMessageQueue();
        Analyser.updateVehicleResult(this);
        this.ageInSteps++;
    }

    public SimVehicle(String vehicleId){
        this.vehicleId = vehicleId;
        this.ageInSteps=0;
        this.messageQueue = new LinkedList<>();
        setVehicleState(VehicleState.NOT_SYNCHRONIZED);

        if(vehicleId.startsWith("normal")){
            this.maxVehicleSpeed = generateClippedNormalDistribution(new Random(),33, 12.3, 24.3, 50);
            this.speedFactor = generateClippedNormalDistribution(new Random(),1.2, 0.15, 0.7, 1.5);
            this.physicalMaxSpeed = 50;
        }
        else {
            this.maxVehicleSpeed = generateClippedNormalDistribution(new Random(),22, 6, 19, 27);
            this.speedFactor = generateClippedNormalDistribution(new Random(),0.90,0.10,0.80,1.10);
            this.physicalMaxSpeed = 25;
        }


        this.isTraffic = false;
        this.flockingActivationStep = 0;
        this.trafficEstimationsSinceLastChange = new HashMap<>();
        this.numberOfEmergencyBraking=0;

        this.laneChangeNeeded=false;
        this.stepOfLastLaneChange=0;

        reinitNormalVehicleBehavior();
    }

    public void setVehicleState(VehicleState vehicleState) {
        if(this.vehicleState == vehicleState) return;
        this.vehicleState = vehicleState;
    }

    private void clearCarToXMessageQueue(){
        messageQueue = new LinkedList<>();
    }

    public void setTraffic(boolean traffic) {
        if(this.isTraffic == traffic) return;
        this.isTraffic = traffic;
        if(this.isTraffic) flockingActivationStep = Main.step;
        if(!this.isTraffic) {
            flockingDeactivationStep = Main.step;
            reinitNormalVehicleBehavior();
            Vehicle.setSpeed(vehicleId,maxVehicleSpeed);
        }
    }

    public void reinitNormalVehicleBehavior(){
        Vehicle.setSpeedMode(vehicleId, 31);
        //Vehicle.setLaneChangeMode(vehicleId, 1621);
        if(Vehicle.getTypeID(vehicleId).startsWith("normal")){
            Vehicle.setMinGap(vehicleId,3);
            Vehicle.setMaxSpeed(vehicleId, maxVehicleSpeed);
            Vehicle.setSpeedFactor(vehicleId,speedFactor);
            Vehicle.setParameter(vehicleId, "laneChangeModel", "LC2013");
            Vehicle.setParameter(vehicleId, "carFollowModel", "Krauss");
            Vehicle.setParameter(vehicleId, "lcStrategic", "0.8");
            Vehicle.setParameter(vehicleId, "lcCooperative", "0.2");
            Vehicle.setParameter(vehicleId, "lcSpeedGain", "0.5");
            Vehicle.setParameter(vehicleId, "lcKeepRight", "0.5");
            Vehicle.setParameter(vehicleId, "lcAssertive", "0.7");
            Vehicle.setAccel(vehicleId,2.8);
            Vehicle.setDecel(vehicleId,4.7);
            Vehicle.setEmergencyDecel(vehicleId,12);
            Vehicle.setParameter(vehicleId, "sigma", "0.8");
            Vehicle.setTau(vehicleId,1.4);
        }
        else if(Vehicle.getTypeID(vehicleId).startsWith("truck")){
            Vehicle.setMinGap(vehicleId,3.5);
            Vehicle.setLength(vehicleId,12);
            Vehicle.setMaxSpeed(vehicleId,maxVehicleSpeed);
            Vehicle.setSpeedFactor(vehicleId,speedFactor);
            Vehicle.setParameter(vehicleId, "laneChangeModel", "LC2013");
            Vehicle.setParameter(vehicleId, "carFollowModel", "Krauss");
            Vehicle.setParameter(vehicleId, "lcStrategic", "0.4");
            Vehicle.setParameter(vehicleId, "lcCooperative", "0.4");
            Vehicle.setParameter(vehicleId, "lcSpeedGain", "0.7");
            Vehicle.setParameter(vehicleId, "lcKeepRight", "1.0");
            Vehicle.setParameter(vehicleId, "lcAssertive", "0.4");
            Vehicle.setAccel(vehicleId,1.0);
            Vehicle.setDecel(vehicleId,3.5);
            Vehicle.setEmergencyDecel(vehicleId,12);
            Vehicle.setParameter(vehicleId, "sigma", "0.5");
            Vehicle.setTau(vehicleId,2.0);
        }
    }

    private static double generateClippedNormalDistribution(Random random, double mean, double stddev, double min, double max) {
        double value;
        do {
            // Generiere eine normalverteilte Zufallszahl
            value = mean + stddev * random.nextGaussian();
        } while (value < min || value > max);

        return value;
    }

    public double getDesiredSpeed() {
        return Double.min(maxVehicleSpeed,maxRoadSpeed);
    }

    public double getMaxAllowedAndPhysicalSpeed(){
        return Double.min(physicalMaxSpeed,maxRoadSpeed);
    }

    public void emergencyBrake() {
        if(currentSpeed > 0) this.numberOfEmergencyBraking++;
    }

    public double getDistanceToEndOfCurrentLane() {
        return distancesToLaneEnd.getOrDefault(lane,Double.MAX_VALUE);
    }
}
