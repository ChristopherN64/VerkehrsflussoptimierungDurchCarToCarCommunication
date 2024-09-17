package main.debugger;

import main.vehicle.SimVehicle;
import main.vehicle.VehicleGrid;
import org.eclipse.sumo.libtraci.TraCIColor;
import org.eclipse.sumo.libtraci.Vehicle;

public class Debugger {

    public static void debugVehicleState() {
        Vehicle.getIDList().forEach(vehicleId -> {
            SimVehicle simVehicle = VehicleGrid.vehicles.get(vehicleId);
            switch (simVehicle.getVehicleState()) {
                case NOT_SYNCHRONIZED: {
                    Vehicle.setColor(vehicleId, new TraCIColor(0, 0, 255));
                    break;
                }
                case SYNCHRONIZED: {
                    if (simVehicle.getIsTraffic()) Vehicle.setColor(vehicleId, new TraCIColor(0, 255, 0));
                    else Vehicle.setColor(vehicleId, new TraCIColor(255, 0, 0));
                    break;
                }
                case SEARCHING_FOR_CONSENSUS: {
                    Vehicle.setColor(vehicleId, new TraCIColor(0, 255, 255));
                    break;
                }
            }
        });
    }

    public static void debugGrid(){
        VehicleGrid.vehicles.values().forEach(v-> Vehicle.setColor(v.getVehicleId(),new TraCIColor(0,255,0)));
        if( VehicleGrid.vehicleGrid.get("0,0")!=null) VehicleGrid.vehicleGrid.get("0,0").forEach(v->
                Vehicle.setColor(v.getVehicleId(), new TraCIColor(0,0,255))
        );
    }
}
