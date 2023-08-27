package main;

import authorization.SpotifyAPIConnector;
import authorization.AuthenticationURI;
import handlers.SearchRequest;
import enums.MessageType;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import services.Console;
import services.LoginService;
import utils.Config;
import utils.ServerConfiguration;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Array;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class Main {

    public static ServerConfiguration config;

    private HashMap<String, String> userSearch = new HashMap<>();
    private static ArrayList<String> logtIn = new ArrayList<>();
    public ArrayList<String> blockedUsers = new ArrayList<>();
    private static HashMap<String, SimpleDateFormat> currentSong = new HashMap<>();

    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    public static void main(String[] args) throws IOException {

        new Main().startUP();
    }

    public void startUP() throws IOException {
        instance = this;

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
            Console.printout("", MessageType.INFO);
            Console.printout(" It seems like you startet SpotifyLink for the first time. Please update your Spotify API Credentials in the config file!", MessageType.INFO);
            Console.printout("", MessageType.INFO);
        }

        startApp();
        AuthenticationURI.authorizationCodeUri_Sync();

        while (true) {
            Console.reader();
        }
    }

    public void startApp() throws IOException {

        Javalin app = Javalin.create(config -> {
            config.addStaticFiles("WebPages", Location.CLASSPATH);
            config.showJavalinBanner = false;
        }).start(80);

        app.ws("/auth", ws -> {
            ws.onConnect(ctx -> {
                Console.printout("Authentication connected", MessageType.INFO);
            });
            ws.onMessage(ctx -> {
                String message = ctx.message().replace("?", "").replace("code=", "");
                SpotifyAPIConnector.authorizationCode_Sync(message);
            });
        });

        app.ws("/main", ws -> {
            ws.onConnect(ctx -> {
                if (blockedUsers.contains(ctx.session.getRemoteAddress().getAddress().toString().replace("/", ""))) {
                    ctx.closeSession();
                    return;
                }
                Console.printout("User connected to main websocket. (IP: " + ctx.session.getRemoteAddress().getAddress().toString().replace("/", "") + ")", MessageType.INFO);
                try {
                    ctx.send("Song-Name: " + new SpotifyAPIConnector().readCurrentSong());
                    ArtistSimplified[] artists = new SpotifyAPIConnector().currentSongArtist();
                    ctx.send("Song-Artists: " + getArtists(artists));
                    ctx.send("Song-Cover: " + new SpotifyAPIConnector().getAlbumCover());
                    ctx.send("Song-Url: " + new SpotifyAPIConnector().getURL());

                } catch (Exception e1) {
                    ctx.send("Not-playing");
                }
            });
            ws.onClose(ctx -> {
                Console.printout("User disconnected from main websocket. (IP: " + ctx.session.getRemoteAddress().getAddress().toString().replace("/", "") + ")", MessageType.INFO);
            });
            ws.onMessage(ctx -> {
                if (blockedUsers.contains(ctx.session.getRemoteAddress().getAddress().toString().replace("/", ""))) {
                    ctx.closeSession();
                    return;
                }
                if (ctx.message().equals("BACK")) {
                    new SpotifyAPIConnector().songBack();
                } else if (ctx.message().equals("PAUSE")) {
                    new SpotifyAPIConnector().playPauseSong();
                } else if (ctx.message().equals("VORWARD")) {
                    new SpotifyAPIConnector().songVorward();
                } else if (ctx.message().equalsIgnoreCase("refresh")) {
                    try {
                        ctx.send("Song-Name: " + new SpotifyAPIConnector().readCurrentSong());
                        ArtistSimplified[] artists = new SpotifyAPIConnector().currentSongArtist();
                        ctx.send("Song-Artists: " + getArtists(artists));
                        ctx.send("Song-Cover: " + new SpotifyAPIConnector().getAlbumCover());
                    } catch (Exception e1) {
                        ctx.send("Not-playing");
                    }

                    // TODO Cache damit nicht immer neue Abfrage von SpotifyAPIConnector gemacht wird Hashmap mit Zeit und dem aktuellen Song
                    // TODO dann überprüfen ob Zeit unter 3 sek war und sonst abfrage an Spotify senden
                } else if (ctx.message().contains("Search:")) {
                    if (ctx.message().replace("Search: ", "").equalsIgnoreCase("")) {
                        return;
                    }
                    if (userSearch.containsKey(ctx.getSessionId())) {
                        if (userSearch.get(ctx.getSessionId()).equalsIgnoreCase(ctx.message())) {
                            return;
                        }
                    }
                    try {
                        String message = ctx.message();

                        Paging<Track> trackPaging = SearchRequest.searchRequest(message.replace("Search: ", ""));
                        ctx.send("search-1-name: " + trackPaging.getItems()[0].getName());
                        ctx.send("search-1-artists: " + getArtists(trackPaging.getItems()[0].getArtists()));
                        ctx.send("search-1-cover: " + trackPaging.getItems()[0].getAlbum().getImages()[0].getUrl());
                        ctx.send("search-1-uri: " + trackPaging.getItems()[0].getUri());

                        ctx.send("search-2-name: " + trackPaging.getItems()[1].getName());
                        ctx.send("search-2-artists: " + getArtists(trackPaging.getItems()[1].getArtists()));
                        ctx.send("search-2-cover: " + trackPaging.getItems()[1].getAlbum().getImages()[0].getUrl());
                        ctx.send("search-2-uri: " + trackPaging.getItems()[1].getUri());

                        ctx.send("search-3-name: " + trackPaging.getItems()[2].getName());
                        ctx.send("search-3-artists: " + getArtists(trackPaging.getItems()[2].getArtists()));
                        ctx.send("search-3-cover: " + trackPaging.getItems()[2].getAlbum().getImages()[0].getUrl());
                        ctx.send("search-3-uri: " + trackPaging.getItems()[2].getUri());

                        userSearch.put(ctx.getSessionId(), message);
                    } catch (Exception e1) {
                    }

                } else if (ctx.message().contains("Song-Play")) {
                    if (ctx.message().replace("Song-Play: ", "").equalsIgnoreCase("undefined")) {
                        return;
                    }
                    new SpotifyAPIConnector().addSongtoList(ctx.message().replace("Song-Play: ", ""));
                }
            });
        });
        app.ws("/login", ws -> {
            ws.onMessage(ctx -> {
                if (LoginService.login(ctx.message(), ctx.getSessionId())) {
                    logtIn.add(ctx.getSessionId());
                    ctx.send("CORRECT " + ctx.getSessionId());
                    Console.printout("User " + ctx.session.getRemoteAddress() + "logged into Admin account!", MessageType.INFO);
                } else {
                    ctx.send("WRONG");
                }
            });
        });
        app.ws("/admin", ws -> {
            ws.onMessage(ctx -> {
                String message = ctx.message();
                if (message.contains("LOGIN")) {
                    message = message.replace("LOGIN", "").replace("?", "");

                    if (logtIn.contains(message)) {
                        logtIn.add(ctx.getSessionId());
                        logtIn.remove(message);

                        ctx.send("CONFIRMED");
                    } else {
                        ctx.send("CLOSE");
                    }

                }
            });
        });
        app.get("/login", ctx -> {
            ctx.render("/WebPages/login.html");
        });
        app.get("/admin", ctx -> {
            ctx.render("/WebPages/admin.html");
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
        } else if (artists.length == 5) {
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName() + ", " + artists[3].getName() + ", " + artists[4].getName();
        } else if (artists.length == 6) {
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName() + ", " + artists[3].getName() + ", " + artists[4].getName() + ", " + artists[5].getName();
        } else {
            return "ERROR";
        }
    }

    private static String getTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
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
