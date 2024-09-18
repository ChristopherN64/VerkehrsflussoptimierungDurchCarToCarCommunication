package main.consensus;

import main.communication.CarToCarCommunication;
import main.vehicle.SimVehicle;
import main.vehicle.Cache;
import main.vehicle.VehicleState;

import java.util.HashMap;
import java.util.Random;

public class Consensus {
    private static final double PERCENTAGE_OF_NEIGHBORS = 0.33;
    private static final double NEIGHBOR_RADIUS_FOR_GOSSIP_ALGORITHM = 100;
    private static final double CHANGE_OF_OPINION_THRESHOLD = 0.5;
    private static final double CHANGE_OF_OPINION_COOLDOWN = 3;

    private static final double MINIMUM_SPEED_PERCENTAGE_WITHOUT_TRAFFIC = 0.4;
    private static final double NEIGHBOR_RADIUS_FOR_TRAFFIC = 20;
    private static final double MINIMUM_NUMBER_OF_NEIGHBOURS = 5;

    public static void simulateConsensus(SimVehicle vehicle) {
        if(vehicle.getVehicleState()==VehicleState.NOT_SYNCHRONIZED) vehicle.setVehicleState(VehicleState.SEARCHING_FOR_CONSENSUS);
        vehicle.setVehicleState(VehicleState.SYNCHRONIZED);

        //Calculate own traffic estimation
        boolean ownTrafficEstimation = Consensus.calculateOwnTrafficEstimation(vehicle);
        //Send own traffic estimation to PERCENTAGE_OF_NEIGHBORS
        sendMessageToRandomNumberOfNeighbors(vehicle.getVehicleId(), String.valueOf(ownTrafficEstimation));
        //Get traffic estimations of neighbours (From CarToXMessages)
        HashMap<Boolean, Integer> neighbourEstimations = Consensus.getNeighbourTrafficEstimation(vehicle);
        //Set own isTraffic based on own traffic estimation and neighbour traffic estimation
        Consensus.updateOwnTrafficEstimation(vehicle,ownTrafficEstimation,neighbourEstimations);
    }

    //Calculate own Traffic estimation based on speed and number of Neighbours in radius NEIGHBOR_RADIUS_FOR_TRAFFIC
    //Traffic estimation is true if one of them is greater than the threshold
    public static boolean calculateOwnTrafficEstimation(SimVehicle vehicle){
        double speedPercentage = vehicle.getCurrentSpeed() / Double.min(vehicle.getMaxVehicleSpeed(),vehicle.getMaxRoadSpeed());
        double numberOfNeighbours = Cache.getNeighbors(vehicle.getVehicleId(),NEIGHBOR_RADIUS_FOR_TRAFFIC).size();
        return (speedPercentage < MINIMUM_SPEED_PERCENTAGE_WITHOUT_TRAFFIC || numberOfNeighbours > MINIMUM_NUMBER_OF_NEIGHBOURS);
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
        return neighbourEstimations;
    }

    public static void updateOwnTrafficEstimation(SimVehicle vehicle, boolean ownTrafficEstimation, HashMap<Boolean, Integer> neighbourEstimations) {
        if(((double) neighbourEstimations.get(true) / (neighbourEstimations.get(false) + neighbourEstimations.get(true)) > CHANGE_OF_OPINION_THRESHOLD)) setIsTrafficWithCooldown(vehicle,true);
        else if(((double) neighbourEstimations.get(false) / (neighbourEstimations.get(false) + neighbourEstimations.get(true)) > CHANGE_OF_OPINION_THRESHOLD)) setIsTrafficWithCooldown(vehicle,false);
        else setIsTrafficWithCooldown(vehicle,ownTrafficEstimation);
    }

    public static void sendMessageToRandomNumberOfNeighbors(String senderId, String data) {
        // Wähle zufällig ein oder mehrere Fahrzeuge aus, an die die Nachricht weitergegeben wird
        Random random = new Random();
        Cache.getNeighbors(senderId, NEIGHBOR_RADIUS_FOR_GOSSIP_ALGORITHM)
                .stream().filter(n -> random.nextDouble() < PERCENTAGE_OF_NEIGHBORS)
                .forEach(neighbor->CarToCarCommunication.sendMessageToVehicle(senderId, neighbor.getVehicleId(), data));

    }

    public static void setIsTrafficWithCooldown(SimVehicle vehicle,boolean isTraffic) {
        vehicle.getTrafficEstimationsSinceLastChange().put(isTraffic, vehicle.getTrafficEstimationsSinceLastChange().getOrDefault(isTraffic, 0) + 1);
        if(vehicle.getLastIsTrafficChange() > CHANGE_OF_OPINION_COOLDOWN) {
            vehicle.setTraffic(vehicle.getTrafficEstimationsSinceLastChange().getOrDefault(true,0) > vehicle.getTrafficEstimationsSinceLastChange().getOrDefault(false,0));
            vehicle.setTrafficEstimationsSinceLastChange(new HashMap<>());
            vehicle.setLastIsTrafficChange(0);
        }
        vehicle.setLastIsTrafficChange(vehicle.getLastIsTrafficChange() + 1);
    }
}
