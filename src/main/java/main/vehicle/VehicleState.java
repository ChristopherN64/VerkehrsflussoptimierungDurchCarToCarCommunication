package main.vehicle;

public enum VehicleState {
    SYNCHRONIZED,
    NOT_SYNCHRONIZED,
    SEARCHING_FOR_CONSENSUS,
    COLLIDED,
    UNDER_DISTANCE,
    IN_DISTANCE,
    OUT_OF_DISTANCE,
    NO_LEADER,
    FINISHED;
}
