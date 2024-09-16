package main;

import lombok.Data;
import main.analytics.Analyser;
import main.communication.CarToXMessage;
import main.consensus.EpidemicGossipAlgorithm;
import org.apache.commons.lang3.tuple.MutablePair;
import org.eclipse.sumo.libtraci.TraCIColor;
import org.eclipse.sumo.libtraci.TraCIPosition;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;


@Data
public class SimVehicle {

    private String vehicleId;
    private MutablePair<Double,Double> position;
    private int ageInSteps;
    private VehicleState vehicleState;
    private Queue<CarToXMessage> messageQueue;

    private boolean isTraffic;
    private int consensusIsTrafficCount;
    private int consensusIsNoTrafficCount;

    public void simulateStep(){
        processMessages();

        simulateConsensus();

        simulateFlocking();

        Analyser.updateVehicleResult(this);
        ageInSteps++;
    }

    private void simulateConsensus() {
        if(vehicleState==VehicleState.NOT_SYNCHRONIZED) {
            setVehicleState(VehicleState.SEARCHING_FOR_CONSENSUS);
        }
        EpidemicGossipAlgorithm.sendMessageToRandomNumberOfNeighbors(vehicleId,String.valueOf(isTraffic));
        if(consensusIsTrafficCount>5) {
            isTraffic = true;
            Vehicle.setColor(vehicleId,new TraCIColor(0,255,0));
            consensusIsTrafficCount=0;
            consensusIsNoTrafficCount=0;
            setVehicleState(VehicleState.SYNCHRONIZED);
        }
        if(consensusIsNoTrafficCount>5) {
            isTraffic = false;
            Vehicle.setColor(vehicleId,new TraCIColor(0,0,255));
            consensusIsTrafficCount=0;
            consensusIsNoTrafficCount=0;
            setVehicleState(VehicleState.SYNCHRONIZED);
        }
    }

    private void simulateFlocking() {

    }

    public SimVehicle(String vehicleId){
        this.vehicleId = vehicleId;
        this.ageInSteps=0;
        this.messageQueue = new LinkedList<>();
        setVehicleState(VehicleState.NOT_SYNCHRONIZED);

        Random rand = new Random();
        isTraffic = rand.nextInt(3)>=1;
        consensusIsTrafficCount=0;
        consensusIsNoTrafficCount=0;
    }

    public void setVehicleState(VehicleState vehicleState) {
        if(this.vehicleState==vehicleState) return;
        this.vehicleState = vehicleState;
        switch (vehicleState){
            case NOT_SYNCHRONIZED: {
                Vehicle.setColor(vehicleId,new TraCIColor(255,0,0));
                break;
            }
            case SYNCHRONIZED: {
                if(isTraffic) Vehicle.setColor(vehicleId,new TraCIColor(0,255,0));
                else Vehicle.setColor(vehicleId,new TraCIColor(0,0,255));
                break;
            }
            case SEARCHING_FOR_CONSENSUS:{
                Vehicle.setColor(vehicleId,new TraCIColor(0,255,255));
                break;
            }
        }
    }

    private void processMessage(CarToXMessage message){
        if(Boolean.parseBoolean(message.getData())) consensusIsTrafficCount++;
        else consensusIsNoTrafficCount++;
    }

    private void processMessages(){
        while(!messageQueue.isEmpty()) processMessage(messageQueue.poll());
    }
}
