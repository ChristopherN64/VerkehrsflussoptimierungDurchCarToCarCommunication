package main;

import lombok.Data;
import main.communication.CarToCarCommunication;
import main.communication.CarToXMessage;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.LinkedList;
import java.util.Queue;


@Data
public class SimVehicle {

    private String vehicleId;
    private Queue<CarToXMessage> messageQueue;

    public void simulateStep(){
        processMessages();
    }

    public SimVehicle(String vehicleId){
        this.vehicleId = vehicleId;
        this.messageQueue = new LinkedList<>();
    }

    private void processMessage(CarToXMessage message){
        System.out.println("Recieved message: "+ message);
        if(message.getData().equals("Bist du da?")) CarToCarCommunication.sendMessageToVehicle(vehicleId,message.getSender(),"Ja, was?");
        else {
            Vehicle.setSpeed(vehicleId,Double.parseDouble(message.getData()));
            Vehicle.setLaneChangeMode(vehicleId,512);
            Vehicle.changeLane(vehicleId,2,1.0);
        }
    }

    private void processMessages(){
        while(!messageQueue.isEmpty()) processMessage(messageQueue.poll());
    }
}
