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

    private String vehicleId;
    private MutablePair<Double,Double> position;
    private int ageInSteps;
    private VehicleState vehicleState;
    private LinkedList<CarToXMessage> messageQueue;

    private double currentSpeed;
    private double maxVehicleSpeed;
    private double maxRoadSpeed;

    private double distanceToLeadingVehicle;

    private boolean isTraffic;
    private int lastIsTrafficChange;
    private HashMap<Boolean,Integer> trafficEstimationsSinceLastChange;

    public void simulateStep(){
        if(Main.SIMULATE_CONSENSUS)  Consensus.simulateConsensus(this);

        if(Main.SIMULATE_FLOCKING) Flocking.simulateFlocking(this);

        clearMessages();
        Analyser.updateVehicleResult(this);
        ageInSteps++;
    }

    public SimVehicle(String vehicleId){
        this.vehicleId = vehicleId;
        this.ageInSteps=0;
        this.messageQueue = new LinkedList<>();
        setVehicleState(VehicleState.NOT_SYNCHRONIZED);

        this.isTraffic = false;
        this.lastIsTrafficChange = 0;
        this.trafficEstimationsSinceLastChange = new HashMap<>();

        initVehicleBehavior();
    }

    public void setVehicleState(VehicleState vehicleState) {
        if(this.vehicleState==vehicleState) return;
        this.vehicleState = vehicleState;
    }

    private void clearMessages(){
        messageQueue = new LinkedList<>();
    }

    public void setTraffic(boolean traffic) {
        if(this.isTraffic == traffic) return;
        this.isTraffic = traffic;
        if(!this.isTraffic) initVehicleBehavior();
    }

    public void initVehicleBehavior(){
        Vehicle.setSpeed(vehicleId,-1);
        Vehicle.setLaneChangeMode(vehicleId,-1);

        if(Vehicle.getTypeID(vehicleId).startsWith("normal")){
            Vehicle.setMinGap(vehicleId,2.7);
            //generateClippedNormalDistribution(new Random(),120, 30, 100, 180)
            Vehicle.setMaxSpeed(vehicleId, generateClippedNormalDistribution(new Random(),33, 8.3, 27.3, 50));
            Vehicle.setSpeedFactor(vehicleId,generateClippedNormalDistribution(new Random(),1.2, 0.15, 0.7, 1.5));
            Vehicle.setParameter(vehicleId, "laneChangeModel", "LC2013");
            Vehicle.setParameter(vehicleId, "lcStrategic", "0.8");
            Vehicle.setParameter(vehicleId, "lcCooperative", "0.2");
            Vehicle.setParameter(vehicleId, "lcSpeedGain", "0.3");
            Vehicle.setParameter(vehicleId, "lcKeepRight", "0.7");
            Vehicle.setParameter(vehicleId, "lcAssertive", "0.7");
            Vehicle.setParameter(vehicleId, "carFollowModel", "Krauss");
            Vehicle.setAccel(vehicleId,2.8);
            Vehicle.setDecel(vehicleId,4.7);
            Vehicle.setEmergencyDecel(vehicleId,8);
            Vehicle.setParameter(vehicleId, "sigma", "0.6");
            Vehicle.setTau(vehicleId,1.2);
        }
        else if(Vehicle.getTypeID(vehicleId).startsWith("truck")){
            Vehicle.setMinGap(vehicleId,3.5);
            Vehicle.setLength(vehicleId,12);
            Vehicle.setMaxSpeed(vehicleId,generateClippedNormalDistribution(new Random(),25, 6, 20, 29));
            Vehicle.setSpeedFactor(vehicleId,generateClippedNormalDistribution(new Random(),0.90,0.10,0.80,1.10));
            Vehicle.setParameter(vehicleId, "laneChangeModel", "LC2013");
            Vehicle.setParameter(vehicleId, "lcStrategic", "0.4");
            Vehicle.setParameter(vehicleId, "lcCooperative", "0.4");
            Vehicle.setParameter(vehicleId, "lcSpeedGain", "0.7");
            Vehicle.setParameter(vehicleId, "lcKeepRight", "1.0");
            Vehicle.setParameter(vehicleId, "lcAssertive", "0.4");
            Vehicle.setParameter(vehicleId, "carFollowModel", "Krauss");
            Vehicle.setAccel(vehicleId,1.0);
            Vehicle.setDecel(vehicleId,3.5);
            Vehicle.setEmergencyDecel(vehicleId,8);
            Vehicle.setParameter(vehicleId, "sigma", "0.5");
            Vehicle.setTau(vehicleId,2.0);
        }
    }

    private static double generateClippedNormalDistribution(Random random, double mean, double stddev, double min, double max) {
        double value;
        do {
            // Generiere eine normalverteilte Zufallszahl
            value = mean + stddev * random.nextGaussian();
        } while (value < min || value > max); // Beschneiden, wenn außerhalb des Bereichs

        return value;
    }
}
