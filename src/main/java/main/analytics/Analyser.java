package main.analytics;

import main.Main;
import main.flocking.Flocking;
import main.vehicle.Cache;
import main.vehicle.SimVehicle;
import main.vehicle.VehicleState;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

public class Analyser {
    public static HashMap<String,VehicleResult> vehicleResults = new HashMap<>();

    public static void init(){
        vehicleResults = new HashMap<>();
    }

    public static void updateVehicleResult(SimVehicle vehicle){
        if(!vehicleResults.containsKey(vehicle.getVehicleId())) vehicleResults.put(vehicle.getVehicleId(),new VehicleResult(vehicle));
        else vehicleResults.get(vehicle.getVehicleId()).addStep(vehicle);
    }

    public static void printAnalytics(){
        System.out.println("Simuliertes Szenario: "+ Main.SIMULATION_SZENARIO);
        System.out.println("Flocking: "+ Main.SIMULATE_FLOCKING);

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
        System.out.println("Durchschnittliche Zurückgelegte Distanz in Metern:"+vehicleResults.values().stream().map(VehicleResult::getTraveledDistance).filter(d->d>0).mapToDouble(Double::doubleValue).average().getAsDouble());
        System.out.println("______________________________________________________________________");
        writeAnalyticsToCSV();
    }


    public static void writeAnalyticsToCSV() {
        String csvFile = "analytics.csv";
        File file = new File(csvFile);
        boolean fileExists = file.exists();

        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter writer = new PrintWriter(bw)) {

            // Wenn die Datei neu ist, Überschriften hinzufügen
            if (!fileExists) {
                String header = "Version,Timestamp,Simuliertes Szenario,Steps,Flocking,Fertige Fahrzeuge,Emergency Brakes,Kollisionen,avg Zeit,avg Distanz in Metern,UNDER_DISTANCE,IN_DISTANCE,OUT_OF_DISTANCE,NO_LEADER,Flocking Config";
                writer.println(header);
            }

            // Daten sammeln
            String version = Main.version;
            String simuliertesSzenario = Main.SIMULATION_SZENARIO.toString();
            String flocking = String.valueOf(Main.SIMULATE_FLOCKING);

            long fertigeFahrzeuge = vehicleResults.values().stream()
                    .filter(vehicleResult -> vehicleResult.getLastVehicleState() == VehicleState.FINISHED)
                    .count();

            double durchschnittlicheZeit = vehicleResults.values().stream()
                    .map(vehicleResult -> vehicleResult.getLastStep() - vehicleResult.getCreationStep())
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0);

            double durchschnittlicheDistanz = vehicleResults.values().stream()
                    .map(VehicleResult::getTraveledDistance)
                    .filter(d -> d > 0)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);

            // Datenzeile erstellen
            StringBuilder sb = new StringBuilder();
            sb.append(version).append(',');
            sb.append(LocalDateTime.now()).append(',');
            sb.append(simuliertesSzenario).append(',');
            sb.append(Main.step).append(',');
            sb.append(flocking).append(',');
            sb.append(fertigeFahrzeuge).append(',');
            sb.append(vehicleResults.values().stream().mapToInt(VehicleResult::getEmergencyBrakes).sum()).append(',');
            sb.append(Cache.collisions.size()).append(',');
            sb.append(durchschnittlicheZeit).append(',');
            sb.append(durchschnittlicheDistanz).append(',');
            sb.append(getAnalysisOfState(VehicleState.UNDER_DISTANCE)).append(',');
            sb.append(getAnalysisOfState(VehicleState.IN_DISTANCE)).append(',');
            sb.append(getAnalysisOfState(VehicleState.OUT_OF_DISTANCE)).append(',');
            sb.append(getAnalysisOfState(VehicleState.NO_LEADER)).append(',');
            sb.append("Radius: "+ Flocking.COHESION_NEIGHBOUR_RADIUS+" "+"Cooldown: "+Flocking.COHESION_LANE_CHANGE_COOLDOWN+" MinOffset: "+Flocking.COHESION_MINIMUM_UTILIZATION_OFFSET_ON_NEW_LANE);



            // Datenzeile schreiben
            writer.println(sb.toString());
            System.out.println("Analysedaten wurden zur Datei " + csvFile + " hinzugefügt.");

        } catch (IOException e) {
            System.out.println("Fehler beim Schreiben der CSV-Datei: " + e.getMessage());
        }
    }

    public static String getAnalysisOfState(VehicleState vehicleState){
        String ret = "";
        ret+= "min:"+ vehicleResults.values().stream().map(VehicleResult::getVehicleStates).map(vehicleStates->vehicleStates.get(vehicleState)).filter(Objects::nonNull).min(Integer::compareTo).orElse(-1);
        ret+= "  avg:"+ vehicleResults.values().stream().map(VehicleResult::getVehicleStates).map(vehicleStates->vehicleStates.get(vehicleState)).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(-1);
        ret+= "  max:"+ vehicleResults.values().stream().map(VehicleResult::getVehicleStates).map(vehicleStates->vehicleStates.get(vehicleState)).filter(Objects::nonNull).max(Integer::compareTo).orElse(-1);
        return ret;
    }


}
