package main.stauerkennung;

import main.vehicle.Cache;
import main.vehicle.SimVehicle;

public class Stauerkennung {
    //Konstanten für Traffic
    public static double MINIMUM_SPEED_PERCENTAGE_WITHOUT_TRAFFIC = 0.7;
    public static double NEIGHBOR_RADIUS_FOR_TRAFFIC = 50;
    public static double MINIMUM_NUMBER_OF_NEIGHBOURS_FOR_TRAFFIC = 3;

    //Calculate own Traffic estimation based on speed and number of Neighbours in radius NEIGHBOR_RADIUS_FOR_TRAFFIC
    //Traffic estimation is true if one of them is greater than the threshold
    public static boolean calculateOwnTrafficEstimation(SimVehicle vehicle){
        double speedPercentage = vehicle.getCurrentSpeed() / vehicle.getDesiredSpeed();
        double numberOfNeighbours = Cache.getNeighbors(vehicle.getVehicleId(),NEIGHBOR_RADIUS_FOR_TRAFFIC).size();

        return (speedPercentage < MINIMUM_SPEED_PERCENTAGE_WITHOUT_TRAFFIC && numberOfNeighbours > MINIMUM_NUMBER_OF_NEIGHBOURS_FOR_TRAFFIC);
    }
}
