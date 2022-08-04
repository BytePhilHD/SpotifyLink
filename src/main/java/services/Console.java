package services;

import enums.MessageType;

public class Console {

    public static void printout(String input, MessageType messageType) {
        System.out.println(messageType + " > " + input);
    }
}
