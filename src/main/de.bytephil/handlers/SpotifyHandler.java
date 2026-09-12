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

    /** How long an on demand request may reuse the last answer from Spotify. */
    private static final long CACHE_TTL_MS = 1000;

    private final SpotifyAPIConnector spotifyAPI = Main.spotifyConnector;
    private Instant cacheTime;
    private String cachedQueueIds;
    private List<SongObject> cachedQueuObjects = new ArrayList<>();

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

    /**
     * Asks Spotify for the current queue. The track details behind the queue entries are
     * only looked up again when the first three entries actually changed, which saves a
     * second request on every poll where the queue stayed the same.
     */
    public synchronized List<SongObject> refreshQueue() {
        List<IPlaylistItem> userQueue = spotifyAPI.getUsersQueue();
        cacheTime = Instant.now();

        if (userQueue == null) {
            return cachedQueuObjects;
        }

        String[] ids = userQueue.stream().limit(3).map(IPlaylistItem::getId).toArray(String[]::new);
        String signature = String.join(",", ids);
        if (signature.equals(cachedQueueIds)) {
            return cachedQueuObjects;
        }

        if (ids.length == 0) {
            cachedQueueIds = signature;
            cachedQueuObjects = new ArrayList<>();
            return cachedQueuObjects;
        }

        Track[] tracks = SearchRequest.getSeveralTracks_Sync(ids);
        if (tracks == null) {
            return cachedQueuObjects;
        }

        List<SongObject> songObjects = new ArrayList<>();
        for (Track track : tracks) {
            if (track == null) {
                continue;
            }
            songObjects.add(new SongObject(track.getName(), getFirstArtist(track), getAlbumImageUrl(track),
                    track.getUri(), false));
        }

        cachedQueueIds = signature;
        cachedQueuObjects = songObjects;
        return songObjects;
    }

    /**
     * Cached view on the queue for requests that a client asked for directly.
     */
    public synchronized List<SongObject> getQueueAsSongObjects() {
        if (cacheTime == null || Duration.between(cacheTime, Instant.now()).toMillis() >= CACHE_TTL_MS) {
            return refreshQueue();
        }
        return cachedQueuObjects;
    }

    private String getFirstArtist(Track track) {
        if (track.getArtists() == null || track.getArtists().length == 0) {
            return "";
        }
        return track.getArtists()[0].getName();
    }

    private String getAlbumImageUrl(Track track) {
        if (track.getAlbum() == null || track.getAlbum().getImages() == null
                || track.getAlbum().getImages().length == 0) {
            return "";
        }
        return track.getAlbum().getImages()[0].getUrl();
    }
}