package main.vehicle;

import lombok.Data;
import main.analytics.Analyser;
import main.communication.CarToXMessage;
import main.consensus.Consensus;
import main.flocking.Flocking;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.LinkedList;


@Data
public class SimVehicle {

    private String vehicleId;
    private MutablePair<Double,Double> position;
    private int ageInSteps;
    private VehicleState vehicleState;
    private LinkedList<CarToXMessage> messageQueue;

    private Boolean isTraffic;
    private int consensusIsTrafficCount;
    private int consensusIsNoTrafficCount;

    public void simulateStep(){
        Consensus.simulateConsensus(this);

        Flocking.simulateFlocking(this);

        clearMessages();
        Analyser.updateVehicleResult(this);
        ageInSteps++;
    }

    public SimVehicle(String vehicleId){
        this.vehicleId = vehicleId;
        this.ageInSteps=0;
        this.messageQueue = new LinkedList<>();
        setVehicleState(VehicleState.NOT_SYNCHRONIZED);

        this.isTraffic = null;
        consensusIsTrafficCount=0;
        consensusIsNoTrafficCount=0;
    }

    public void setVehicleState(VehicleState vehicleState) {
        if(this.vehicleState==vehicleState) return;
        this.vehicleState = vehicleState;
    }

    private void clearMessages(){
        messageQueue = new LinkedList<>();
    }
}
