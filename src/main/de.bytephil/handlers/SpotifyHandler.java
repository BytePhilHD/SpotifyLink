package handlers;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ParseException;
import org.json.JSONObject;

import authorization.SpotifyAPIConnector;
import entities.SongObject;
import enums.MessageType;
import main.Main;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import services.Console;

public class SpotifyHandler {

    private final SpotifyAPIConnector spotifyAPI = Main.spotifyConnector;

    private Instant requestTime;
    private List<SongObject> cachedQueuObjects;
    private Instant requestTimeSong;
    private JSONObject cachedSong;

    public int getDurationtoSong(String url) {
        double lengthInSeconds = 0.0;
        List<IPlaylistItem> userQueue = spotifyAPI.getUsersQueue();
        if (userQueue.isEmpty()) {
            return -1;
        }
        try {
            lengthInSeconds += (double) spotifyAPI.getCurrentTrackItem().getDurationMs() / 1000;
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (IPlaylistItem item : userQueue) {
            if (item.getUri().equals(url)) {
                return (int) Math.round(lengthInSeconds / 60.0);
            } else {
                lengthInSeconds += (double) item.getDurationMs() / 1000;
            }
        }
        return (int) Math.round(lengthInSeconds / 60.0);
    }

    public synchronized List<SongObject> getQueueAsSongObjects() {
        if (requestTime == null) {
            requestTime = Instant.now();
        } else if (Duration.between(requestTime, Instant.now()).getSeconds() >= 3) {
            List<IPlaylistItem> userQueue = spotifyAPI.getUsersQueue();
            List<SongObject> songObjects = new ArrayList<>();

            Track[] tracks = SearchRequest
                    .getSeveralTracks_Sync(
                            userQueue.stream().limit(3).map(IPlaylistItem::getId).toArray(String[]::new));

            for (Track track : tracks) {
                songObjects.add(new SongObject(track.getName(), track.getArtists()[0].getName(),
                        track.getAlbum().getImages()[0].getUrl(),
                        track.getUri(), false));
            }

            requestTime = Instant.now();
            cachedQueuObjects = songObjects;
            return songObjects;
        }
        return cachedQueuObjects;
    }

    public synchronized JSONObject getCurrentTrackInfo() throws IOException, SpotifyWebApiException, ParseException {
        if (requestTimeSong == null) {
            requestTimeSong = Instant.now();
        } else if (Duration.between(requestTimeSong, Instant.now()).getSeconds() >= 2) {
            try {
                Track track = spotifyAPI.getCurrentTrackItem();
                JSONObject trackInfo = new JSONObject();
                trackInfo.put("name", track.getName());
                trackInfo.put("artists", getArtists(track.getArtists()));
                trackInfo.put("cover", track.getAlbum().getImages()[0].getUrl());
                trackInfo.put("uri", track.getUri());
                requestTimeSong = Instant.now();
                cachedSong = trackInfo;
                return trackInfo;

            } catch (NullPointerException e) {
                Console.printError("Error in getCurrentTrackInfo ", MessageType.ERROR, e);
                return null;
            }
        }
        return cachedSong;
    }

    private String getArtists(ArtistSimplified[] artists) {
        StringBuilder artistsNames = new StringBuilder();
        for (ArtistSimplified artist : artists) {
            artistsNames.append(artist.getName()).append(", ");
        }
        // Remove the trailing comma and space
        if (artistsNames.length() > 0) {
            artistsNames.setLength(artistsNames.length() - 2);
        }
        return artistsNames.toString();
    }
}