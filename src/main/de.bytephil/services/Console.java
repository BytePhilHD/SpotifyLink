package services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

import enums.MessageType;

public class Console {

    public static void printout(String message, MessageType messageType) {
        System.out.println("[" + getTime() + "] " + messageType + " - " + message);
    }

    public static void printError(String message, MessageType messageType, Throwable throwable) {
        System.out.println("[" + getTime() + "] " + messageType + " - " + message);
        throwable.printStackTrace(System.out);
    }

    private static String getTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static void empty() {
        System.out.println("");
    }

    public static void reader() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String input = null;
        try {
            input = reader.readLine();
        } catch (IOException e1) {
        }
    }
}
