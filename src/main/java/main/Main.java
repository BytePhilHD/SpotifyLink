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
import utils.ServerConfiguration;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.HashMap;

public class Main {

    public static ServerConfiguration config;

    private HashMap<String, String> userSearch = new HashMap<>();

    public static void main(String[] args) throws IOException {

        new Main().startUP();
    }

    public void startUP() throws IOException {
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

       // reader();
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
                Console.printout("User connected to main websocket.", MessageType.INFO);
                try {
                    ctx.send("Song-Name: " + new SpotifyAPIConnector().readCurrentSong());
                    ArtistSimplified[] artists = new SpotifyAPIConnector().currentSongArtist();
                    ctx.send("Song-Artists: " + getArtists(artists));
                    ctx.send("Song-Cover: " + new SpotifyAPIConnector().getAlbumCover());
                } catch (Exception e1) {
                    ctx.send("Not-playing");
                    Console.printout("Main websocket found no playing song", MessageType.ERROR);
                }
            });
            ws.onMessage(ctx -> {
                if (ctx.message().equalsIgnoreCase("refresh")) {
                    try {
                        ctx.send("Song-Name: " + new SpotifyAPIConnector().readCurrentSong());
                        ArtistSimplified[] artists = new SpotifyAPIConnector().currentSongArtist();
                        ctx.send("Song-Artists: " + getArtists(artists));
                        ctx.send("Song-Cover: " + new SpotifyAPIConnector().getAlbumCover());
                    } catch (Exception e1) {
                        ctx.send("Not-playing");
                    }

                    // TODO Cache damit nicht immer neue Abfrage von SpotifyAPIConnector gemacht wird
                }
                else if (ctx.message().contains("Search:")) {
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
                    } catch (Exception e1) {}

                }
                else if (ctx.message().contains("Song-Play")) {
                    if (ctx.message().replace("Song-Play: ", "").equalsIgnoreCase("undefined")) {
                        return;
                    }
                    new SpotifyAPIConnector().addSongtoList(ctx.message().replace("Song-Play: ", ""));

                    // TODO send message to user that selected song is now in the queue
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
        } else if (artists.length == 5) {
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName() + ", " + artists[3].getName() + ", " + artists[4].getName();
        } else if (artists.length == 6) {
            return artists[0].getName() + ", " + artists[1].getName() + ", " + artists[2].getName() + ", " + artists[3].getName() + ", " + artists[4].getName() + ", " + artists[5].getName();
        }
        else {
            return "ERROR";
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

    /*
else if (wsinput.includes("search-1-name")) {
             document.getElementById("search-1-name").innerHTML = wsinput.replace("search-1-name: ", "");
         } else if (wsinput.includes("search-1-artists")) {
             document.getElementById("search-1-artists").innerHTML = wsinput.replace("search-1-artists: ", "");
         } else if (wsinput.includes("search-1-cover")) {
             document.getElementById("search-1-cover").src = wsinput.replace("search-1-cover: ", "");
         }

     }
     const input = document.querySelector('#searchbar');
     const log = document.getElementById('log');

     input.addEventListener('change', updateValue);

     function updateValue() {
         ws.send("Search: " + input.value);
     }
     */

}
