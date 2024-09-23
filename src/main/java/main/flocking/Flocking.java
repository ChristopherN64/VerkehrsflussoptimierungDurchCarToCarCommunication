package main.flocking;

import main.vehicle.SimVehicle;
import org.eclipse.sumo.libtraci.Vehicle;

public class Flocking {
    public static void simulateFlocking(SimVehicle vehicle) {
        if (vehicle.isTraffic()) {
            String vehicleId = vehicle.getVehicleId();
            Vehicle.setLaneChangeMode(vehicleId, 0b00000011);
            Vehicle.setSpeed(vehicleId, 5);
            Vehicle.setMinGap(vehicleId, 2.5);
            Vehicle.setParameter(vehicleId, "lcStrategic", "0.1");
            Vehicle.setParameter(vehicleId, "lcCooperative", "1.0");
            Vehicle.setParameter(vehicleId, "lcSpeedGain", "0.1");
            Vehicle.setParameter(vehicleId, "lcKeepRight", "1.0");
            Vehicle.setParameter(vehicleId, "lcAssertive", "0.1");
            Vehicle.setParameter(vehicleId, "carFollowModel", "Krauss");
            Vehicle.setAccel(vehicleId, 0.4);
            Vehicle.setDecel(vehicleId, 2.5);
            Vehicle.setParameter(vehicleId, "sigma", "0.1");
            Vehicle.setTau(vehicleId, 0.2);

            if (vehicle.getDistanceToLeadingVehicle() > 5) {
                Vehicle.setSpeed(vehicleId, vehicle.getMaxRoadSpeed());
                Vehicle.setLaneChangeMode(vehicleId, -1);
            }
        }
    }
}
