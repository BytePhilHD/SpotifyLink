package main;

import authorization.SpotifyAPIConnector;
import authorization.AuthenticationURI;
import handlers.SearchRequest;
import enums.MessageType;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import services.Console;
import utils.ServerConfiguration;

import java.io.*;
import java.lang.reflect.Array;

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

        startApp();
        AuthenticationURI.authorizationCodeUri_Sync();

        reader();
    }

    public void startApp() throws IOException {

        Javalin app = Javalin.create(config -> {
            config.addStaticFiles("WebPages", Location.CLASSPATH);
            config.showJavalinBanner = false;
        }).start(80);

        app.ws("/auth", ws -> {
            ws.onConnect(ctx -> {
                System.out.println("Connection established.");
            });
            ws.onMessage(ctx -> {
                String message = ctx.message().replace("?", "").replace("code=", "");
                System.out.println("Message received! " + message);
                SpotifyAPIConnector.authorizationCode_Sync(message);
            });
        });

        app.ws("/main", ws -> {
            ws.onConnect(ctx -> {
                Console.printout("User connected to main websocket.", MessageType.INFO);
                try {
                    ctx.send("Song-Name: " + new SpotifyAPIConnector().readCurrentSong());
                    ArtistSimplified[] artists = new SpotifyAPIConnector().currentSongArtist();
                    ctx.send("Song-Artists: " + getArtists(artists));
                    ctx.send("Song-Cover: " + new SpotifyAPIConnector().getAlbumCover());
                } catch (Exception e1) {
                    Console.printout("Main websocket found no playing song", MessageType.ERROR);
                }
            });
            ws.onMessage(ctx -> {
                if (ctx.message().equalsIgnoreCase("refresh")) {
                    Console.printout("Refresh received", MessageType.DEBUG);
                    try {
                        ctx.send("Song-Name: " + new SpotifyAPIConnector().readCurrentSong());
                        ArtistSimplified[] artists = new SpotifyAPIConnector().currentSongArtist();
                        ctx.send("Song-Artists: " + getArtists(artists));
                        ctx.send("Song-Cover: " + new SpotifyAPIConnector().getAlbumCover());
                    } catch (Exception e1) {
                        ctx.send("Not-playing");
                    }
                }
            });
        });
    }

    private String getArtists(ArtistSimplified[] artists) {
        if (artists.length == 1) {
            return artists[0].getName();
        } else if (artists.length == 2) {
            return artists[0].getName() + ", " + artists[1].getName();
        } else if (artists.length == 3) {
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName();
        } else if (artists.length == 4) {
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName() + ", " + artists[3].getName();
        }
        else {
            return "ERROR";
        }
    }

    public static void reader() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("READER");
        String input = null;
        try {
            input = reader.readLine();
            System.out.println("You inputed: " + input);
            String uri = SearchRequest.searchRequest(input);
            System.out.println("URI: " + uri);
            new SpotifyAPIConnector().addSongtoList(uri);
            reader();
        } catch (IOException e1) {
            System.out.println(e1.getMessage());
        }
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
