package handlers;

import authorization.SpotifyAPIConnector;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import java.util.List;

public class SpotifyHandler {
    
    private SpotifyAPIConnector spotifyAPI = new SpotifyAPIConnector();

    public int getDurationtoSong(String url) {
        double lengthInSeconds = 0.0;
        List<IPlaylistItem> userQueue = spotifyAPI.getUsersQueue();
        
        try {
            lengthInSeconds += (double) spotifyAPI.getCurrentTrackItem().getDurationMs() / 1000;
        } catch(Exception e) {
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