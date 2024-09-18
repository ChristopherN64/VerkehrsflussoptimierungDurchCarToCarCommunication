package main.communication;

import main.vehicle.SimVehicle;
import main.vehicle.Cache;

public class CarToCarCommunication {
    public static boolean sendMessageToVehicle(String senderId,String targetId,String message){
        SimVehicle targetVehicle = Cache.vehicles.get(targetId);
        if(targetVehicle==null) return false;
        targetVehicle.getMessageQueue().add(new CarToXMessage(senderId,targetId,message));
        return true;
    }
}
