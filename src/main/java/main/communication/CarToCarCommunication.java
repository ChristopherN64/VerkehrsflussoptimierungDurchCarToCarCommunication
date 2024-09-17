package main.communication;

import main.*;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleGrid;

public class CarToCarCommunication {
    public static boolean sendMessageToVehicle(String senderId,String targetId,String message){
        SimVehicle targetVehicle = VehicleGrid.vehicles.get(targetId);
        if(targetVehicle==null) return false;
        targetVehicle.getMessageQueue().add(new CarToXMessage(senderId,targetId,message));
        return true;
    }
}
