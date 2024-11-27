package main.consensus;

import main.Main;
import main.communication.CarToXMessage;
import main.stauerkennung.Stauerkennung;
import main.vehicle.SimVehicle;
import main.vehicle.Cache;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class Consensus {
    //Konstanten für Gossip-Algorithmus
    private static final double PERCENTAGE_OF_NEIGHBORS_FOR_GOSSIP_ALGORITHM = 1;
    private static final double NEIGHBOR_RADIUS_FOR_GOSSIP_ALGORITHM = 50;
    private static final double CHANGE_OF_OPINION_THRESHOLD = 0.5;
    public static double FLOCKING_DEACTIVATION_COOLDOWN = 60;
    private static final double NEIGHBOUR_ESTIMATION_BULK_SIZE = 3;

    public static void simulateConsensus(SimVehicle vehicle) {
        //Calculate own traffic estimation
        boolean ownTrafficEstimation = Stauerkennung.calculateOwnTrafficEstimation(vehicle);
        //Send own traffic estimation to PERCENTAGE_OF_NEIGHBORS
        sendMessageToPercentageNumberOfNeighbors(vehicle.getVehicleId(), String.valueOf(ownTrafficEstimation));
        //Set own isTraffic based on own traffic estimation and neighbour traffic estimation
        updateOwnTrafficEstimation(vehicle,ownTrafficEstimation);
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

    public static void updateOwnTrafficEstimation(SimVehicle vehicle, boolean ownTrafficEstimation) {
        //Get traffic estimations of neighbours (From CarToXMessages)
        HashMap<Boolean, Integer> neighbourEstimations = Consensus.getNeighbourTrafficEstimation(vehicle);
        //Put own estimation
        putTrafficEstimationInBulk(vehicle,ownTrafficEstimation, 2);
        //Put Neighbour estimation
        int numberOfEstimations = neighbourEstimations.get(false) + neighbourEstimations.get(true);
        putTrafficEstimationInBulk(vehicle,true,3);
        putTrafficEstimationInBulk(vehicle,neighbourEstimations);

        setIsTrafficWithDeactivationCooldown(vehicle);
        if(Main.step % NEIGHBOUR_ESTIMATION_BULK_SIZE == 0) vehicle.setTrafficEstimationsSinceLastChange(new HashMap<>());
    }

    public static void sendMessageToPercentageNumberOfNeighbors(String senderId, String data) {
        List<MutablePair<SimVehicle,Double>> neighbors = Cache.getNeighbors(senderId, NEIGHBOR_RADIUS_FOR_GOSSIP_ALGORITHM);
        neighbors.stream().sorted(Comparator.comparingDouble(MutablePair::getRight)).limit((int) Math.ceil((PERCENTAGE_OF_NEIGHBORS_FOR_GOSSIP_ALGORITHM) * neighbors.size())).toList()
                .forEach(neighbor->sendMessageToVehicle(senderId, neighbor.getLeft().getVehicleId(), data));

    }

    public static void sendMessageToVehicle(String senderId, String targetId, String message){
        SimVehicle targetVehicle = Cache.vehicles.get(targetId);
        if(targetVehicle==null) return;
        targetVehicle.getMessageQueue().add(new CarToXMessage(senderId,targetId,message));
    }

    public static void setIsTrafficWithDeactivationCooldown(SimVehicle vehicle) {
        boolean currentEstimation = vehicle.getTrafficEstimationsSinceLastChange().getOrDefault(true,0) > vehicle.getTrafficEstimationsSinceLastChange().getOrDefault(false,0);
        if(currentEstimation) setIsTraffic(vehicle,true);
        else if(Main.step - vehicle.getFlockingActivationStep() > FLOCKING_DEACTIVATION_COOLDOWN) setIsTraffic(vehicle, false);
    }

    public static void setIsTraffic(SimVehicle vehicle, boolean isTraffic) {
        vehicle.setTraffic(isTraffic);
        if(isTraffic != vehicle.isTraffic()) vehicle.setTrafficEstimationsSinceLastChange(new HashMap<>());
    }

    public static void putTrafficEstimationInBulk(SimVehicle vehicle, boolean estimation, int count) {
        vehicle.getTrafficEstimationsSinceLastChange().put(estimation, vehicle.getTrafficEstimationsSinceLastChange().getOrDefault(estimation, 0) + count);
    }


    private static void putTrafficEstimationInBulk(SimVehicle vehicle, HashMap<Boolean, Integer> neighbourEstimations) {
        neighbourEstimations.forEach((key, value) -> vehicle.getTrafficEstimationsSinceLastChange().put(key, vehicle.getTrafficEstimationsSinceLastChange().getOrDefault(key,0) + neighbourEstimations.getOrDefault(key,2)));
    }
}
