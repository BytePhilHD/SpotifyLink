package handlers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import authorization.SpotifyAPIConnector;
import entities.SongObject;
import main.Main;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.specification.Track;

public class SpotifyHandler {

    private final SpotifyAPIConnector spotifyAPI = Main.spotifyConnector;
    private Instant requestTime;
    private List<SongObject> cachedQueuObjects;

    public int getDurationtoSong(String url) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                CurrentlyPlaying currentlyPlaying = spotifyAPI.getCurrentlyPlayingTrack();
                List<IPlaylistItem> userQueue = spotifyAPI.getUsersQueue();
                if (currentlyPlaying == null || currentlyPlaying.getItem() == null || userQueue == null) {
                    return -1;
                }

                Track currentTrack = (Track) currentlyPlaying.getItem();
                long remainingMs = Math.max(0L, currentTrack.getDurationMs() - currentlyPlaying.getProgress_ms());
                long waitMs = remainingMs;

                for (IPlaylistItem item : userQueue) {
                    if (item.getUri().equals(url)) {
                        return (int) Math.ceil(waitMs / 60000.0);
                    }
                    waitMs += item.getDurationMs();
                }
            } catch (Exception e) {
                return -1;
            }

            if (attempt < 4) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
        }
        return -1;
    }

    public synchronized List<SongObject> getQueueAsSongObjects() {
        if (requestTime == null) {
            requestTime = Instant.now();
        } else if (Duration.between(requestTime, Instant.now()).getSeconds() >= 1) {
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
}