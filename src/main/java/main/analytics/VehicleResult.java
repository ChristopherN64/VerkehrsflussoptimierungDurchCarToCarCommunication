package main.analytics;

import lombok.Data;
import main.Main;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleState;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.HashMap;

@Data
public class VehicleResult {
    private String vehicleId;
    private int creationStep;
    private int lastStep;
    private VehicleState lastVehicleState;
    private HashMap<VehicleState,Integer> vehicleStates;
    private double traveledDistance;

    public VehicleResult(SimVehicle simVehicle) {
        this.vehicleId = simVehicle.getVehicleId();
        this.creationStep = Main.step;
        this.vehicleStates = new HashMap<>();
        addStep(simVehicle);
        this.lastVehicleState = simVehicle.getVehicleState();
    }

    public void addStep(SimVehicle simVehicle) {
        if(!this.vehicleStates.containsKey(simVehicle.getVehicleState())) vehicleStates.put(simVehicle.getVehicleState(),1);
        else vehicleStates.put(simVehicle.getVehicleState(),vehicleStates.get(simVehicle.getVehicleState())+1);
        this.lastStep = Main.step;
        this.lastVehicleState = simVehicle.getVehicleState();
        if(simVehicle.getVehicleState()!=VehicleState.FINISHED) this.traveledDistance = Vehicle.getDistance(vehicleId);
    }
}
