package main.analytics;

import main.Main;
import main.consensus.Consensus;
import main.flocking.Flocking;
import main.stauerkennung.Stauerkennung;
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
        System.out.println("Simuliertes Scenario: "+ Main.SIMULATION_SCENARIO);
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
        System.out.println("Durchschnittliche Zeit:"+vehicleResults.values().stream().filter(vehicleResult -> vehicleResult.getLastVehicleState() != VehicleState.COLLIDED).map(vehicleResult -> vehicleResult.getLastStep()-vehicleResult.getCreationStep()).mapToInt(Integer::intValue).average().getAsDouble());
        System.out.println("Durchschnittliche Zurückgelegte Distanz in Metern:"+vehicleResults.values().stream().filter(vehicleResult -> vehicleResult.getLastVehicleState() != VehicleState.COLLIDED).map(VehicleResult::getTraveledDistance).filter(d->d>0).mapToDouble(Double::doubleValue).average().getAsDouble());
        System.out.println("______________________________________________________________________");
        writeVehicleResultsToCsv();
        writeAnalyticsToCsv();
    }

    public static void writeVehicleResultsToCsv(){
        String csvFile = Main.SIMULATION_SCENARIO+"_"+Main.SIMULATE_FLOCKING+"_"+Main.SIMULATE_CONSENSUS+"_"+Main.SIMULATION_STEPS+".csv";
        File file = new File(csvFile);
        boolean fileExists = file.exists();

        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter writer = new PrintWriter(bw)) {

            // Wenn die Datei neu ist, Überschriften hinzufügen
            if (!fileExists) {
                String header = "vehicleId, time, type, last state, creationStep, lastStep, traveledDistance, emergencyBrakes";
                writer.println(header);
            }

            vehicleResults.values().forEach(vehicleResult->{
                // Datenzeile erstellen
                StringBuilder sb = new StringBuilder();
                sb.append(vehicleResult.getVehicleId()).append(',');;
                sb.append(vehicleResult.getLastStep()-vehicleResult.getCreationStep()).append(',');;
                sb.append(vehicleResult.getVehicleId().startsWith("normal") ? "Car" : "Truck").append(',');;
                sb.append(vehicleResult.getLastVehicleState()).append(',');;
                sb.append(vehicleResult.getCreationStep()).append(',');;
                sb.append(vehicleResult.getLastStep()).append(',');;
                sb.append(vehicleResult.getTraveledDistance()).append(',');;
                sb.append(vehicleResult.getEmergencyBrakes()).append(',');;


                // Datenzeile schreiben
                writer.println(sb.toString());
            });

            System.out.println("Analysedaten wurden zur Datei " + csvFile + " hinzugefügt.");

        } catch (IOException e) {
            System.out.println("Fehler beim Schreiben der CSV-Datei: " + e.getMessage());
        }
    }

    public static void writeAnalyticsToCsv() {
        String csvFile = "validation_"+Main.SIMULATION_STEPS+".csv";
        File file = new File(csvFile);
        boolean fileExists = file.exists();

        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter writer = new PrintWriter(bw)) {

            // Wenn die Datei neu ist, Überschriften hinzufügen
            if (!fileExists) {
                String header = "Version,Timestamp,Simuliertes Scenario,Steps,Flocking,Konsens,Fertige Fahrzeuge,Nicht fertige Fahrzeuge,Emergency Brakes,Kollisionen,min Zeit,avg Zeit,maxZeit,avg Distanz,sum Distanz,UNDER_DISTANCE,IN_DISTANCE,OUT_OF_DISTANCE,NO_LEADER,NEIGHBOR_RADIUS_FOR_TRAFFIC,FLOCKING_DEACTIVATION_COOLDOWN,MAX_MIN_DISTANCE_DIFF,COHESION_MINIMUM_UTILIZATION_OFFSET_ON_NEW_LANE,COHESION_LANE_CHANGE_COOLDOWN,seperationPercentageWhenAligmentAcellarate";
                writer.println(header);
            }

            // Daten sammeln
            String version = Main.version;
            String simuliertesScenario = Main.SIMULATION_SCENARIO.toString();
            String flocking = String.valueOf(Main.SIMULATE_FLOCKING);

            long nichtFertigeFahrzeuge = vehicleResults.values().stream()
                    .filter(vehicleResult -> (vehicleResult.getLastVehicleState() != VehicleState.FINISHED && vehicleResult.getLastVehicleState() != VehicleState.COLLIDED))
                    .count();

            long fertigeFahrzeuge = vehicleResults.values().stream()
                    .filter(vehicleResult -> vehicleResult.getLastVehicleState() == VehicleState.FINISHED)
                    .count();

            double durchschnittlicheZeit = vehicleResults.values().stream()
                    .filter(vehicleResult -> vehicleResult.getLastVehicleState() == VehicleState.FINISHED)
                    .map(vehicleResult -> vehicleResult.getLastStep() - vehicleResult.getCreationStep())
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0);

            double minZeit = vehicleResults.values().stream()
                    .filter(vehicleResult -> vehicleResult.getLastVehicleState() == VehicleState.FINISHED)
                    .map(vehicleResult -> vehicleResult.getLastStep() - vehicleResult.getCreationStep())
                    .mapToInt(Integer::intValue)
                    .min()
                    .orElse(0);

            double maxZeit = vehicleResults.values().stream()
                    .filter(vehicleResult -> vehicleResult.getLastVehicleState() == VehicleState.FINISHED)
                    .map(vehicleResult -> vehicleResult.getLastStep() - vehicleResult.getCreationStep())
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);

            double durchschnittlicheDistanz = vehicleResults.values().stream()
                    .filter(vehicleResult -> vehicleResult.getLastVehicleState() != VehicleState.COLLIDED)
                    .map(VehicleResult::getTraveledDistance)
                    .filter(d -> d > 0)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);

            double summierteDistanz = vehicleResults.values().stream()
                    .filter(vehicleResult -> vehicleResult.getLastVehicleState() != VehicleState.COLLIDED)
                    .map(VehicleResult::getTraveledDistance)
                    .filter(d -> d > 0)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            // Datenzeile erstellen
            StringBuilder sb = new StringBuilder();
            sb.append(version).append(',');
            sb.append(LocalDateTime.now()).append(',');
            sb.append(simuliertesScenario).append(',');
            sb.append(Main.step).append(',');
            sb.append(flocking).append(',');
            sb.append(Main.SIMULATE_CONSENSUS).append(',');
            sb.append(fertigeFahrzeuge).append(',');
            sb.append(nichtFertigeFahrzeuge).append(',');
            sb.append(vehicleResults.values().stream().mapToInt(VehicleResult::getEmergencyBrakes).sum()).append(',');
            sb.append(Cache.collisions.size()).append(',');
            sb.append(minZeit).append(',');
            sb.append(durchschnittlicheZeit).append(',');
            sb.append(maxZeit).append(',');
            sb.append(durchschnittlicheDistanz).append(',');
            sb.append(summierteDistanz).append(',');
            sb.append(getAnalysisOfState(VehicleState.UNDER_DISTANCE)).append(',');
            sb.append(getAnalysisOfState(VehicleState.IN_DISTANCE)).append(',');
            sb.append(getAnalysisOfState(VehicleState.OUT_OF_DISTANCE)).append(',');
            sb.append(getAnalysisOfState(VehicleState.NO_LEADER)).append(',');


            sb.append(Stauerkennung.NEIGHBOR_RADIUS_FOR_TRAFFIC).append(',');
            sb.append(Consensus.FLOCKING_DEACTIVATION_COOLDOWN).append(',');
            sb.append(Flocking.MAX_MIN_DISTANCE_DIFF ).append(',');
            sb.append(Flocking.COHESION_MINIMUM_UTILIZATION_OFFSET_ON_NEW_LANE).append(',');
            sb.append(Flocking.COHESION_LANE_CHANGE_COOLDOWN).append(',');
            sb.append(Flocking.seperationPercentageWhenAligmentAcellarate);



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
