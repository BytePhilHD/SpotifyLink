package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONObject;

import authorization.AuthenticationURI;
import authorization.SpotifyAPIConnector;
import enums.MessageType;
import handlers.SearchRequest;
import handlers.SpotifyHandler;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsConnectContext;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import services.Console;
import services.LoginService;
import utils.ServerConfiguration;

public class Main {

    public static ServerConfiguration config;

    private static final HashMap<String, String> userSearch = new HashMap<>();
    private static final ArrayList<String> logtIn = new ArrayList<>();
    public static ArrayList<String> blockedUsers = new ArrayList<>();
    private static final ArrayList<String> playedSongs = new ArrayList<>();

    private static Main instance;

    public static String refreshToken;

    public static SpotifyAPIConnector spotifyConnector;

    private static SpotifyHandler spotifyAPIHandler;

    public static Main getInstance() {
        return instance;
    }

    private static boolean isRunning = true;

    public static void main(String[] args) throws IOException {
        startUP();
    }

    public static void startUP() throws IOException {
        instance = new Main();

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
        }

        // Start the Javalin server
        startApp();
        AuthenticationURI.authorizationCodeUri_Sync();
        spotifyConnector = new SpotifyAPIConnector();
        spotifyAPIHandler = new SpotifyHandler();
    }

    public static void startApp() throws IOException {
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.staticFiles.add(staticFileConfig -> {
                staticFileConfig.hostedPath = "/";
                staticFileConfig.directory = "/WebPages";
                staticFileConfig.location = Location.CLASSPATH;
            });
            javalinConfig.showJavalinBanner = false;
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

        app.ws("/main", (WsConfig ws) -> {
            ws.onConnect((WsConnectContext ctx) -> {
                if (blockedUsers.contains(ctx.session.getRemoteAddress().toString().replace("/", ""))) {
                    ctx.closeSession();
                }
                if (isRunning == true) {
                    Console.printout(
                            "User connected to main websocket. (IP: "
                                    + (ctx.session.getRemoteAddress() != null
                                            ? ctx.session.getRemoteAddress().toString().replace("/", "")
                                            : "unknown")
                                    + ")",
                            MessageType.INFO);
                    try {
                        JSONObject data = spotifyConnector.getCurrentTrackInfo();
                        if (data != null) {
                            ctx.send(data.toString());
                        }
                    } catch (Exception e1) {
                        if (e1.getMessage() != null && e1.getMessage().contains("The access token expired")) {
                            SpotifyAPIConnector.refreshToken();
                        }
                    }
                }
            });
            ws.onClose(ctx -> {
                Console.printout(
                        "User disconnected from main websocket. (IP: "
                                + (ctx.session.getRemoteAddress() != null
                                        ? ctx.session.getRemoteAddress().toString().replace("/", "")
                                        : "unknown")
                                + ")",
                        MessageType.INFO);
            });
            ws.onMessage(ctx -> {
                if (blockedUsers.contains(ctx.session.getRemoteAddress().toString().replace("/", ""))) {
                    ctx.closeSession();
                    return;
                }
                if (ctx.message().contains("AUTH")) {
                    JSONObject data = new JSONObject(ctx.message());

                    if (logtIn.contains((String) data.get("AUTH"))) {

                        if (data.get("ACTION").equals("PLAYPAUSE")) {
                            spotifyConnector.playPauseSong();
                        } else if (data.get("ACTION").equals("NEXT")) {
                            spotifyConnector.songVorward();
                        } else if (data.get("ACTION").equals("BACK")) {
                            spotifyConnector.songBack();
                        } else if (data.get("ACTION").equals("TOGGLE-STATE")) {
                            isRunning = !isRunning;
                        } else if (data.get("ACTION").equals("CHANGEUSER")) {
                            JSONObject authJsonObject = new JSONObject();
                            authJsonObject.put("auth-url", AuthenticationURI.getAuthorizationURL());
                            ctx.send(authJsonObject.toString());
                        }
                    } else {
                        ctx.send("close");
                    }

                } else if (isRunning == false) {
                    JSONObject songInfo = new JSONObject();
                    songInfo.put("Not-playing", true);
                    ctx.send(songInfo.toString());
                    ctx.closeSession();
                    return;
                }
                if (ctx.message().equalsIgnoreCase("refresh")) {
                    try {
                        JSONObject data = spotifyConnector.getCurrentTrackInfo();
                        if (data != null) {
                            data.put("user", spotifyConnector.getUserName());
                            ctx.send(data.toString());
                        } else {
                            JSONObject songInfo = new JSONObject();
                            songInfo.put("Not-playing", true);
                            songInfo.put("user", spotifyConnector.getUserName());
                            ctx.send(songInfo.toString());
                        }
                    } catch (Exception e1) {
                        if (e1.getMessage() != null && e1.getMessage().contains("The access token expired")) {
                            SpotifyAPIConnector.refreshToken();
                        }
                    }
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
                    spotifyConnector.addSongtoList(url);
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
    }

    private static void copyFile(File dest, String source) throws IOException {
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream(source);
                OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }

    private static String getArtists(ArtistSimplified[] artists) {
        StringBuilder artistNames = new StringBuilder();
        for (ArtistSimplified artist : artists) {
            if (artistNames.length() > 0) {
                artistNames.append(", ");
            }
            artistNames.append(artist.getName());
        }
        return artistNames.toString();
    }

    private static boolean checkSongisQueue(String uri) {
        return playedSongs.contains(uri);
    }
}