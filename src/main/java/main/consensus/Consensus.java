package main.consensus;

import main.Main;
import main.communication.CarToXMessage;
import main.vehicle.SimVehicle;
import main.vehicle.Cache;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class Consensus {
    //Konstanten für Gossip-Algorithmus
    private static final double PERCENTAGE_OF_NEIGHBORS_FOR_GOSSIP_ALGORITHM = 0.8;
    private static final double NEIGHBOR_RADIUS_FOR_GOSSIP_ALGORITHM = 50;
    private static final double CHANGE_OF_OPINION_THRESHOLD = 0.5;
    private static final double FLOCKING_DEACTIVATION_COOLDOWN = 30;
    private static final double NEIGHBOUR_ESTIMATION_BULK_SIZE = 5;

    //Konstanten für Traffic
    public static final double  MINIMUM_SPEED_PERCENTAGE_WITHOUT_TRAFFIC = 0.9;
    private static final double NEIGHBOR_RADIUS_FOR_TRAFFIC = 35;
    private static final double MINIMUM_NUMBER_OF_NEIGHBOURS_FOR_TRAFFIC = 3;

    public static void simulateConsensus(SimVehicle vehicle) {
        //Calculate own traffic estimation
        boolean ownTrafficEstimation = Consensus.calculateOwnTrafficEstimation(vehicle);
        //Send own traffic estimation to PERCENTAGE_OF_NEIGHBORS
        sendMessageToPercentageNumberOfNeighbors(vehicle.getVehicleId(), String.valueOf(ownTrafficEstimation));
        //Set own isTraffic based on own traffic estimation and neighbour traffic estimation
        updateOwnTrafficEstimation(vehicle,ownTrafficEstimation);
    }

    //Calculate own Traffic estimation based on speed and number of Neighbours in radius NEIGHBOR_RADIUS_FOR_TRAFFIC
    //Traffic estimation is true if one of them is greater than the threshold
    public static boolean calculateOwnTrafficEstimation(SimVehicle vehicle){
        double speedPercentage = vehicle.getCurrentSpeed() / vehicle.getDesiredSpeed();
        double numberOfNeighbours = Cache.getNeighbors(vehicle.getVehicleId(),NEIGHBOR_RADIUS_FOR_TRAFFIC).size();
        return (speedPercentage < MINIMUM_SPEED_PERCENTAGE_WITHOUT_TRAFFIC && numberOfNeighbours > MINIMUM_NUMBER_OF_NEIGHBOURS_FOR_TRAFFIC);
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
        //TODO hier eigene Estimation evtl. höher gewichten
        putTrafficEstimationInBulk(vehicle,ownTrafficEstimation);
        //Put Neighbour estimation
        int numberOfEstimations = neighbourEstimations.get(false) + neighbourEstimations.get(true);
        if(((double) neighbourEstimations.get(true) / numberOfEstimations > CHANGE_OF_OPINION_THRESHOLD)) putTrafficEstimationInBulk(vehicle,true);
        else if(((double) neighbourEstimations.get(false) / numberOfEstimations > CHANGE_OF_OPINION_THRESHOLD)) putTrafficEstimationInBulk(vehicle,false);

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
        //TODO evtl. nicht erst bei change sondern nach eigenem Cooldown zurücksetzen
        if(isTraffic != vehicle.isTraffic()) vehicle.setTrafficEstimationsSinceLastChange(new HashMap<>());
    }

    public static void putTrafficEstimationInBulk(SimVehicle vehicle,boolean estimation) {
        vehicle.getTrafficEstimationsSinceLastChange().put(estimation, vehicle.getTrafficEstimationsSinceLastChange().getOrDefault(estimation, 0) + 1);
    }
}
