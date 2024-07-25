package handlers;

import authorization.SpotifyAPIConnector;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import java.util.List;

public class SpotifyHandler {
    
    private SpotifyAPIConnector spotifyAPI = new SpotifyAPIConnector();

    public int getDurationtoSong(String url) {
        List<IPlaylistItem> userQueue = spotifyAPI.getUsersQueue();
        int lengthInSeconds = 0;
        
        try {
            lengthInSeconds += spotifyAPI.getCurrentTrackItem().getDurationMs() / 1000;
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        for (IPlaylistItem item : userQueue) {
            if (item.getUri().equals(url)) {
                return (int) Math.ceil(lengthInSeconds / 60.0);
            } else {
                lengthInSeconds += item.getDurationMs() / 1000;
            }
        }
        
        return -1;
    }
}