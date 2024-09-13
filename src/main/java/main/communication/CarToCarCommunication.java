package main.communication;

import main.*;

public class CarToCarCommunication {
    public static boolean sendMessageToVehicle(String senderId,String targetId,String message){
        SimVehicle targetVehicle = Main.vehicles.get(targetId);
        if(targetVehicle==null) return false;
        targetVehicle.getMessageQueue().add(new CarToXMessage(senderId,targetId,message));
        return true;
    }
}
