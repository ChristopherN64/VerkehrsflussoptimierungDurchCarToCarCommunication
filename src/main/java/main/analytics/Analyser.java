package main.analytics;

import main.SimVehicle;
import main.VehicleState;

import java.util.*;

public class Analyser {
    public static HashMap<String,VehicleResult> vehicleResults = new HashMap<>();

    public static void updateVehicleResult(SimVehicle vehicle){
        if(!vehicleResults.containsKey(vehicle.getVehicleId())) vehicleResults.put(vehicle.getVehicleId(),new VehicleResult(vehicle));
        else vehicleResults.get(vehicle.getVehicleId()).addStep(vehicle);
        //printAnalytics();
    }

    public static void printAnalytics(){
        Arrays.stream(VehicleState.values()).forEach(vehicleState -> {
            vehicleResults.values().stream().map(VehicleResult::getVehicleStates).map(vehicleStates->vehicleStates.get(vehicleState)).filter(Objects::nonNull).mapToInt(Integer::intValue).average().ifPresent(avg->{
                System.out.println("avg:" + vehicleState+" "+avg);
            });
            vehicleResults.values().stream().map(VehicleResult::getVehicleStates).map(vehicleStates->vehicleStates.get(vehicleState)).filter(Objects::nonNull).max(Integer::compareTo).ifPresent(max->{
                System.out.println("max:" + vehicleState+" "+max);
            });
            vehicleResults.values().stream().map(VehicleResult::getVehicleStates).map(vehicleStates->vehicleStates.get(vehicleState)).filter(Objects::nonNull).min(Integer::compareTo).ifPresent(min->{
                System.out.println("min:" + vehicleState+" "+min);
            });
            System.out.println("-------------------------------");

        });
        System.out.println("Fertige Fahrzeuge:"+vehicleResults.values().stream().filter(vehicleResult -> vehicleResult.getLastVehicleState() == VehicleState.FINISHED).count());
        System.out.println("Durchschnittliche Zeit:"+vehicleResults.values().stream().map(vehicleResult -> vehicleResult.getLastStep()-vehicleResult.getCreationStep()).mapToInt(Integer::intValue).average().getAsDouble());
    }
}
