package main.debugger;

import main.vehicle.SimVehicle;
import main.vehicle.Cache;
import org.eclipse.sumo.libtraci.TraCIColor;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.List;

public class Debugger {

    public static void debugVehicleState() {
        Vehicle.getIDList().forEach(vehicleId -> {
            SimVehicle simVehicle = Cache.vehicles.get(vehicleId);
            switch (simVehicle.getVehicleState()) {
                case NOT_SYNCHRONIZED: {
                    Vehicle.setColor(vehicleId, new TraCIColor(0, 0, 255));
                    break;
                }
                case SYNCHRONIZED: {
                    if (simVehicle.isTraffic()) Vehicle.setColor(vehicleId, new TraCIColor(0, 255, 0));
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
        Cache.vehicles.values().forEach(v-> Vehicle.setColor(v.getVehicleId(),new TraCIColor(0,255,0)));
        if( Cache.vehicleGrid.get("0,0")!=null) Cache.vehicleGrid.get("0,0").forEach(v->
                Vehicle.setColor(v.getVehicleId(), new TraCIColor(0,0,255))
        );
    }
}
