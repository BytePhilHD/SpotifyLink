package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import authorization.AuthenticationURI;
import authorization.SpotifyAPIConnector;
import entities.SongObject;
import enums.MessageType;
import enums.UserType;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Main instance;

    public static String refreshToken;

    public static String sessionCode;

    public static SpotifyAPIConnector spotifyConnector;

    private static SpotifyHandler spotifyAPIHandler;

    public static Main getInstance() {
        return instance;
    }

    private static boolean isRunning = true;

    @lombok.Setter
    private static boolean startingUp = true;

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
        generateSessionCode(5);
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
                ctx.send(sessionCode);
            });
        });

        app.ws("/main", (WsConfig ws) -> {
            ws.onConnect((WsConnectContext ctx) -> {
                if (blockedUsers.contains(ctx.session.getRemoteAddress().toString().replace("/", ""))) {
                    ctx.closeSession();
                }
                if (isRunning && !startingUp) {
                    Console.printout(
                            "User connected to main websocket. (IP: "
                                    + (ctx.session.getRemoteAddress() != null
                                            ? ctx.session.getRemoteAddress().toString().replace("/", "")
                                            : "unknown")
                                    + ")",
                            MessageType.INFO);
                    try {
                        JSONObject data = spotifyAPIHandler.getCurrentTrackInfo();
                        if (data != null) {
                            ctx.send(data.toString());
                        }
                    } catch (IOException | ParseException | SpotifyWebApiException | NullPointerException e1) {
                        if (e1.getMessage() != null && e1.getMessage().contains("The access token expired")) {
                            SpotifyAPIConnector.refreshToken();
                        } else {
                            Console.printError(refreshToken, MessageType.ERROR, e1);
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
                final String content = ctx.message();
                final JSONObject messageJSONObject = new JSONObject(content);

                UserType userType = checkSessionCode(messageJSONObject);
                if (userType == UserType.FORBIDDEN) {
                    ctx.send("forbidden");
                    return;
                }

                if (userType == UserType.ADMIN) {

                    if (logtIn.contains((String) messageJSONObject.get("adminCode"))) {

                        if (messageJSONObject.get("action").equals("PLAYPAUSE")) {
                            spotifyConnector.playPauseSong();
                        } else if (messageJSONObject.get("action").equals("NEXT")) {
                            spotifyConnector.songVorward();
                        } else if (messageJSONObject.get("action").equals("BACK")) {
                            spotifyConnector.songBack();
                        } else if (messageJSONObject.get("action").equals("TOGGLE-STATE")) {
                            isRunning = !isRunning;
                        } else if (messageJSONObject.get("action").equals("CHANGEUSER")) {
                            JSONObject authJsonObject = new JSONObject();
                            authJsonObject.put("auth-url", AuthenticationURI.getAuthorizationURL());
                            ctx.send(authJsonObject.toString());
                        } else if (messageJSONObject.get("action").equals("NEW-SESSION")) {
                            generateSessionCode(5);
                        }
                    } else {
                        ctx.send("close");
                    }

                }
                if (!isRunning || startingUp) {
                    JSONObject songInfo = new JSONObject();
                    songInfo.put("Not-playing", true);
                    songInfo.put("sessionCode", sessionCode);
                    ctx.send(songInfo.toString());
                    return;
                }

                if (messageJSONObject.get("action").equals("refresh")) {
                    try {
                        JSONObject data = spotifyAPIHandler.getCurrentTrackInfo();
                        if (data != null) {
                            if (userType == UserType.ADMIN) {
                                data.put("user", spotifyConnector.getUserName());
                                data.put("sessionCode", sessionCode);
                            } else if (messageJSONObject.get("content").equals("queue")) {
                                data.put("user", "Unbekannt");
                                String jsonString = objectMapper
                                        .writeValueAsString(spotifyAPIHandler.getQueueAsSongObjects());
                                JSONObject response = new JSONObject();
                                response.put("type", "queue");
                                response.put("results", new JSONArray(jsonString));
                                ctx.send(response.toString());
                            }
                            ctx.send(data.toString());
                        } else {
                            JSONObject songInfo = new JSONObject();
                            songInfo.put("Not-playing", true);
                            String username = spotifyConnector.getUserName();
                            if (username != null) {
                                songInfo.put("user", username);
                            } else {
                                songInfo.put("user", "Unbekannt");
                            }
                            songInfo.put("sessionCode", sessionCode);
                            ctx.send(songInfo.toString());
                        }
                    } catch (Exception e1) {
                        if (e1.getMessage() != null && e1.getMessage().contains("The access token expired")) {
                            SpotifyAPIConnector.refreshToken();
                        }
                    }
                } else if (messageJSONObject.get("action").equals("search")) {
                    String searchQuery = messageJSONObject.get("content").toString();
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
                        List<SongObject> songList = new ArrayList<>();

                        for (int i = 0; i < 3; i++) {
                            SongObject songObject = new SongObject(
                                    trackPaging.getItems()[i].getName(),
                                    getArtists(trackPaging.getItems()[i].getArtists()),
                                    trackPaging.getItems()[i].getAlbum().getImages()[0].getUrl(),
                                    trackPaging.getItems()[i].getUri(),
                                    checkSongisQueue(trackPaging.getItems()[i].getUri()));

                            songList.add(songObject);
                        }
                        String jsonString = objectMapper.writeValueAsString(songList);
                        JSONObject response = new JSONObject();
                        response.put("type", "search");
                        response.put("results", new JSONArray(jsonString));
                        ctx.send(response.toString());
                        userSearch.put(ctx.sessionId(), ctx.message());
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                } else if (messageJSONObject.get("action").equals("add-song")) {
                    String url = messageJSONObject.get("content").toString();
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

    private static void generateSessionCode(int length) {
        String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom RANDOM = new SecureRandom();
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        sessionCode = code.toString();
        Console.printout("", MessageType.INFO);
        Console.printout("SessionCode: " + sessionCode, MessageType.INFO);
        Console.printout("", MessageType.INFO);

    }

    private static UserType checkSessionCode(JSONObject content) {
        if (content.has("sessionCode")) {
            if (content.get("sessionCode").equals(sessionCode)) {
                return UserType.USER;
            }
        } else if (content.has("adminCode")) {
            if (logtIn.contains((String) content.get("adminCode"))) {
                return UserType.ADMIN;
            }
        }
        return UserType.FORBIDDEN;
    }
}