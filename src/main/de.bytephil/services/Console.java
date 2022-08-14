package services;

import enums.MessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Console {

    public static void printout(String message, MessageType messageType) {
        System.out.println("[" + getTime() + "] " + messageType + " - " + message);
    }
    private static String getTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static void sendHelp() {
        empty();
        System.out.println("                                SpotifyLink HELP");
        Console.empty();
        Console.printout("You are running SpotifyLink version ALPHA-0.0.1", MessageType.INFO);         // TODO Add version?
        Console.empty();
    }

    public static void empty() {
        System.out.println("");
    }
    public static void reader() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String input = null;
        try {
            input = reader.readLine();
            new ConsoleCommands().handleCommand(input);
        } catch (IOException e1) {}
    }
}
