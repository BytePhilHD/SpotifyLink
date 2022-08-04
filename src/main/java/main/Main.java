package main;

import authorization.AuthorizationCodeExample;
import authorization.AuthorizationCodeUriExample;
import authorization.SearchRequest;
import enums.MessageType;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import services.Console;
import handlers.ConsoleCommandHandler;
import utils.ServerConfiguration;

import java.io.*;


import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
public class Main {

    public static ServerConfiguration config;

    public static void main(String[] args) throws IOException {

        new Main().startUP();
    }

    public void startUP() throws IOException {
        System.out.println("Spotify Artist Search. Please type in a Artist name");

        if (!new File("server.cfg").exists()) {
            final File newFile = new File("server.cfg");
            copyFile(newFile, "default.cfg");
        }

        // Load config
        config = new ServerConfiguration("server.cfg");
        if (config.loaded) {
            Console.printout("Config was successfully loaded!", MessageType.INFO);
        } else {
            Console.printout("Config not loaded! Using default.", MessageType.WARNING);
        }

        AuthorizationCodeUriExample.authorizationCodeUri_Sync();
        startApp();

        //reader();
    }

    public void startApp() throws IOException {

        Javalin app = Javalin.create(config -> {
            config.addStaticFiles("WebPages", Location.CLASSPATH);
            config.showJavalinBanner = false;
        }).start(80);

        app.ws("/main", ws -> {
            ws.onConnect(ctx -> {
                System.out.println("Connection established.");
            });
            ws.onMessage(ctx -> {
                String message = ctx.message().replace("?", "").replace("code=", "");
                System.out.println("Message received! " + message);
                AuthorizationCodeExample.authorizationCode_Sync(message);
            });
        });
    }

    public static void reader() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String input = null;
        try {
            input = reader.readLine();
            SearchRequest.clientCredentials_Sync(input);
        } catch (IOException e1) {}
    }

    public void copyFile(File newFile, String existingFile) throws IOException {
        newFile.createNewFile();
        final FileOutputStream configOutputStream = new FileOutputStream(newFile);
        byte[] buffer = new byte[4096];
        final InputStream defaultConfStream = getClass().getClassLoader().getResourceAsStream(existingFile);
        int readBytes;
        while ((readBytes = defaultConfStream.read(buffer)) > 0) {
            configOutputStream.write(buffer, 0, readBytes);
        }
        defaultConfStream.close();
    }
}
