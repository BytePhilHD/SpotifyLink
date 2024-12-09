package handlers;

import java.util.ArrayList;
import java.util.List;

import authorization.SpotifyAPIConnector;
import entities.SongObject;
import main.Main;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.Track;

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

    public List<SongObject> getQueueAsSongObjects() {
        List<IPlaylistItem> userQueue = spotifyAPI.getUsersQueue();
        List<SongObject> songObjects = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            IPlaylistItem iPlaylistItem = userQueue.get(i);
            Track item = SearchRequest.getTrackById(iPlaylistItem.getId());
            songObjects.add(new SongObject(item.getName(), item.getArtists()[0].getName(),
                    item.getAlbum().getImages()[0].getUrl(), item.getUri(), false));
        }
        return songObjects;
    }
}