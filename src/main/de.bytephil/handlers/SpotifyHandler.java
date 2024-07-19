package handlers;

import authorization.SpotifyAPIConnector;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import java.util.List;

public class SpotifyHandler{
    
    private SpotifyAPIConnector spotifyAPI = new SpotifyAPIConnector();

    public int getDurationtoSong(String url) {
        List<IPlaylistItem> userqueue = spotifyAPI.getUsersQueue();
        int length = 0;
        try {
            length = spotifyAPI.getCurrentTrackItem().getDurationMs()/1000;
        } catch(Exception e1) {}
        for (int i = 0; i < userqueue.size(); i++) {
            if (userqueue.get(i).getUri().equals(url)) {
                return length/60; 
            } else {
                length = length + (userqueue.get(i).getDurationMs()/1000);
            }
        }
        return 0;
    }
}