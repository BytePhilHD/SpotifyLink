package services;

import enums.MessageType;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Console {

    public static void printout(String message, MessageType messageType) {
        System.out.println("[" + getTime() + "] " + messageType + " - " + message);
    }
    private static String getTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
