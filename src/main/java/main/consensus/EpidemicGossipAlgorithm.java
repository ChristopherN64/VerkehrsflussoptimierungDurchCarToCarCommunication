package main.consensus;

import main.Main;
import main.SimVehicle;
import main.communication.CarToCarCommunication;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.sumo.libtraci.Vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class EpidemicGossipAlgorithm {
    private static final double PERCENTAGE_OF_NEIGHBORS = 0.01;
    private static final double NEIGHBOR_RADIUS = 200;

    // Methode zur Verbreitung der Nachricht
    public static void sendMessageToRandomNumberOfNeighbors(String senderId, String data) {
        List<SimVehicle> neighbors = Main.getNeighbors(senderId,NEIGHBOR_RADIUS);
        Random random = new Random();
        neighbors = neighbors.stream().filter(n -> random.nextDouble() < PERCENTAGE_OF_NEIGHBORS).toList();
        // Wähle zufällig ein oder mehrere Fahrzeuge aus, an die die Nachricht weitergegeben wird
        for (SimVehicle neighbor : neighbors) {
            CarToCarCommunication.sendMessageToVehicle(senderId, neighbor.getVehicleId(), data);
        }
    }
}
