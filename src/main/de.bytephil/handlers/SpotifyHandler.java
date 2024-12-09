package handlers;

import java.util.List;

import authorization.SpotifyAPIConnector;
import main.Main;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;

public class SpotifyHandler {

    private final SpotifyAPIConnector spotifyAPI = Main.spotifyConnector;

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
}