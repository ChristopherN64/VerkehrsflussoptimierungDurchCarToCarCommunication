package main.consensus;

import main.communication.CarToCarCommunication;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleGrid;
import main.vehicle.VehicleState;

import java.util.HashMap;
import java.util.Random;

public class Consensus {
    private static final double PERCENTAGE_OF_NEIGHBORS = 5;
    private static final double NEIGHBOR_RADIUS = 100;
    private static final int CHANGE_OF_OPINION_THRESHOLD = 5;

    public static void simulateConsensus(SimVehicle vehicle) {
        if(vehicle.getVehicleState()==VehicleState.NOT_SYNCHRONIZED) vehicle.setVehicleState(VehicleState.SEARCHING_FOR_CONSENSUS);

        vehicle.setIsTraffic(Consensus.getOwnTrafficEstimation(vehicle));

        HashMap<Boolean, Integer> neighbourEstimations = Consensus.getNeighbourTrafficEstimation(vehicle);
        neighbourEstimations.forEach((key,value)->{
            if(key) vehicle.setConsensusIsTrafficCount(vehicle.getConsensusIsTrafficCount()+value);
            else vehicle.setConsensusIsNoTrafficCount(vehicle.getConsensusIsNoTrafficCount()+value);
        });

        Consensus.updateOwnTrafficEstimation(vehicle);
    }

    public static HashMap<Boolean,Integer> getNeighbourTrafficEstimation(SimVehicle vehicle) {
        HashMap<Boolean,Integer> neighbourEstimations = new HashMap<>();
        neighbourEstimations.put(true, 0);
        neighbourEstimations.put(false, 0);

        //processAllConsensusMessages
        vehicle.getMessageQueue().forEach(message->{
            boolean neighbourEstimation = Boolean.parseBoolean(message.getData());
            neighbourEstimations.put(neighbourEstimation,neighbourEstimations.get(neighbourEstimation) + 1);
        });

        sendMessageToRandomNumberOfNeighbors(vehicle.getVehicleId(), String.valueOf(vehicle.getIsTraffic()));
        return neighbourEstimations;
    }

    public static boolean getOwnTrafficEstimation(SimVehicle vehicle){
        Random rand = new Random();
        if(vehicle.getIsTraffic()==null) {
           return rand.nextInt(3)>=1;
        }
        return vehicle.getIsTraffic();
    }

    public static void updateOwnTrafficEstimation(SimVehicle vehicle) {
        if(vehicle.getConsensusIsTrafficCount()>CHANGE_OF_OPINION_THRESHOLD) {
            vehicle.setIsTraffic(true);
            vehicle.setConsensusIsTrafficCount(0);
            vehicle.setConsensusIsNoTrafficCount(0);
            vehicle.setVehicleState(VehicleState.SYNCHRONIZED);
        }
        if(vehicle.getConsensusIsNoTrafficCount()>CHANGE_OF_OPINION_THRESHOLD) {
            vehicle.setIsTraffic(false);
            vehicle.setConsensusIsTrafficCount(0);
            vehicle.setConsensusIsNoTrafficCount(0);
            vehicle.setVehicleState(VehicleState.SYNCHRONIZED);
        }
    }


    public static void sendMessageToRandomNumberOfNeighbors(String senderId, String data) {
        // Wähle zufällig ein oder mehrere Fahrzeuge aus, an die die Nachricht weitergegeben wird
        Random random = new Random();
        VehicleGrid.getNeighbors(senderId,NEIGHBOR_RADIUS)
                .stream().filter(n -> random.nextDouble() < (PERCENTAGE_OF_NEIGHBORS / 100))
                .forEach(neighbor->CarToCarCommunication.sendMessageToVehicle(senderId, neighbor.getVehicleId(), data));

    }
}
