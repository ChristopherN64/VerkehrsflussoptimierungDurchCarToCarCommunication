package main.communication;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CarToXMessage {
    private String sender;
    private String target;
    private String data;
}
