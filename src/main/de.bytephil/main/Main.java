package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONObject;

import authorization.AuthenticationURI;
import authorization.SpotifyAPIConnector;
import enums.MessageType;
import handlers.SearchRequest;
import handlers.SpotifyHandler;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import services.Console;
import services.LoginService;
import utils.ServerConfiguration;

public class Main {

    public static ServerConfiguration config;

    private HashMap<String, String> userSearch = new HashMap<>();
    private static ArrayList<String> logtIn = new ArrayList<>();
    public ArrayList<String> blockedUsers = new ArrayList<>();

    public ArrayList<String> playedSongs = new ArrayList<>();

    private static Main instance;

    public static String refreshToken;

    SpotifyAPIConnector spotifyConnector;

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
            Console.printout(
                    " It seems like you startet SpotifyLink for the first time. Please update your spotifyConnector API Credentials in the config file!",
                    MessageType.INFO);
            Console.printout("", MessageType.INFO);
        }

        startApp();
        AuthenticationURI.authorizationCodeUri_Sync();
        spotifyConnector = new SpotifyAPIConnector();
    }

    public void startApp() throws IOException {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFileConfig -> {
                staticFileConfig.hostedPath = "/";
                staticFileConfig.directory = "WebPages";
                staticFileConfig.location = Location.CLASSPATH;
            });
            config.showJavalinBanner = false;
        }).start(config.port);

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
                if (blockedUsers.contains(ctx.session.getRemoteAddress().toString().replace("/", ""))) {
                    ctx.closeSession();
                    return;
                }
                Console.printout(
                        "User connected to main websocket. (IP: "
                                + ctx.session.getRemoteAddress().toString().replace("/", "") + ")",
                        MessageType.INFO);
                try {
                    JSONObject data = spotifyConnector.getCurrentTrackInfo();
                    if (data == null) {
                        JSONObject songInfo = new JSONObject();
                        songInfo.put("Not-playing", true);
                        ctx.send(songInfo.toString());
                    } else {
                        ctx.send(data.toString());
                    }

                } catch (Exception e1) {
                    if (e1.getMessage().contains("The access token expired")) {
                        SpotifyAPIConnector.refreshToken();
                    }
                }
            });
            ws.onClose(ctx -> {
                Console.printout(
                        "User disconnected from main websocket. (IP: "
                                + ctx.session.getRemoteAddress().toString().replace("/", "") + ")",
                        MessageType.INFO);
            });
            ws.onMessage(ctx -> {
                SpotifyHandler spotifyAPIHandler = new SpotifyHandler();
                if (blockedUsers.contains(ctx.session.getRemoteAddress().toString().replace("/", ""))) {
                    ctx.closeSession();
                    return;
                }
                if (ctx.message().contains("AUTH")) {
                    JSONObject data = new JSONObject(ctx.message());

                    if (logtIn.contains(data.get("AUTH"))) {
                        if (data.get("ACTION").equals("PLAYPAUSE")) {
                            spotifyConnector.playPauseSong();
                        }
                    } else {
                        ctx.send("close");
                    }

                }
                if (ctx.message().equalsIgnoreCase("refresh")) {
                    try {

                        JSONObject data = spotifyConnector.getCurrentTrackInfo();
                        ctx.send(data.toString());

                    } catch (Exception e1) {
                        JSONObject songInfo = new JSONObject();
                        songInfo.put("Not-playing", true);
                        ctx.send(songInfo.toString());
                    }

                    // TODO Cache damit nicht immer neue Abfrage von SpotifyAPIConnector gemacht
                    // wird Hashmap mit Zeit und dem aktuellen Song
                    // TODO dann überprüfen ob Zeit unter 3 sek war und sonst abfrage an spotifyConnector
                    // senden
                    // TODO spotifyConnector.getUsersQueue(); implementieren (Response ist
                    // List<IPlaybackItem>)
                } else if (ctx.message().contains("Search:")) {
                    String searchQuery = ctx.message().replace("Search: ", "");
                    if (searchQuery.equalsIgnoreCase("")) {
                        return;
                    }
                    if (userSearch.containsKey(ctx.sessionId())) {
                        if (userSearch.get(ctx.sessionId()).equalsIgnoreCase(ctx.message())) {
                            return;
                        }
                    }
                    try {
                        Paging<Track> trackPaging = SearchRequest.searchRequest(searchQuery);
                        JSONObject searchResults = new JSONObject();
                        for (int i = 0; i < 3; i++) {
                            JSONObject trackInfo = new JSONObject();
                            trackInfo.put("name", trackPaging.getItems()[i].getName());
                            trackInfo.put("artists", getArtists(trackPaging.getItems()[i].getArtists()));
                            trackInfo.put("cover", trackPaging.getItems()[i].getAlbum().getImages()[0].getUrl());
                            trackInfo.put("uri", trackPaging.getItems()[i].getUri());
                            trackInfo.put("played", checkSongisQueue(trackPaging.getItems()[i].getUri()));
                            searchResults.put("search-" + (i + 1), trackInfo);
                        }
                        ctx.send(searchResults.toString());
                        userSearch.put(ctx.sessionId(), ctx.message());
                    } catch (Exception e1) {
                    }
                } else if (ctx.message().contains("Song-Play")) {
                    String url = ctx.message().replace("Song-Play: ", "");
                    if (url.equalsIgnoreCase("undefined")) {
                        return;
                    }
                    new SpotifyAPIConnector().addSongtoList(url);
                    playedSongs.add(url);
                    ctx.send("QUEUE-LENGTH: " + spotifyAPIHandler.getDurationtoSong(url));
                }
            });
        });

        app.ws("/login", ws -> {
            ws.onMessage(ctx -> {
                if (LoginService.login(ctx.message(), ctx.sessionId())) {
                    logtIn.add(ctx.sessionId());
                    ctx.send("CORRECT " + ctx.sessionId());
                    Console.printout("User " + ctx.session.getRemoteAddress() + " logged into Admin account!",
                            MessageType.INFO);
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
                        logtIn.add(ctx.sessionId());
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
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName() + ", "
                    + artists[3].getName();
        } else if (artists.length == 5) {
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName() + ", "
                    + artists[3].getName() + ", " + artists[4].getName();
        } else if (artists.length == 6) {
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName() + ", "
                    + artists[3].getName() + ", " + artists[4].getName() + ", " + artists[5].getName();
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

    public boolean checkSongisQueue(String url) {
        if (playedSongs.contains(url)) {
            return true;
        }
        else {
            return false;
        }
    }
}
