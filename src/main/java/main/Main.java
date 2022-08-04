package main;

import handlers.ConsoleCommandHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args) {
        System.out.println("Spotify Artist Search. Please type in a Artist name");
        reader();
    }

    //TODO Add File Server (

    public static void reader() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String input = null;
        try {
            input = reader.readLine();
            new ConsoleCommandHandler().handleCommand(input);
        } catch (IOException e1) {}
    }
}
