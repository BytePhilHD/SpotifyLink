package authorization;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ParseException;
import org.json.JSONObject;

import enums.MessageType;
import main.Main;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import services.Console;

public class SpotifyAPIConnector {
    private static final String CLIENT_ID = Main.config.clientID;
    private static final String CLIENT_SECRET = Main.config.clientSecret;
    private static final URI redirectUri = SpotifyHttpManager.makeUri(Main.config.webaddress + "auth.html");
    public static String code = "";
    private static final long PAUSE_BETWEEN_REQUESTS_MS = 200;
    /** How long an on demand request may reuse the last answer from Spotify. */
    private static final long CACHE_TTL_MS = 1000;

    private Instant cacheTime;
    private JSONObject cachedSong;
    private static String cachedUserName;

    private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRedirectUri(redirectUri)
            .build();

    private String currentTrackId = null;
    private ArtistSimplified[] currentTrackArtists = null;
    private String currentAlbumCover = null;

    public static void authorizationCode_Sync(String code1) {
        try {
            final AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(code1)
                    .build().execute();

            // Set access and refresh token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

            // A different account may have been authenticated, so the cached name is stale.
            cachedUserName = null;

            Console.printout("Authentication successful!", MessageType.INFO);
            Main.setStartingUp(false);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            if (e.getMessage().contains("Authorization code expired")) {
                refreshToken();
            } else {
                Console.printError("Error at SpotifyAPIConnector", MessageType.ERROR, e);
            }
        }
    }

    public static void refreshToken() {
        try {
            final AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCodeRefresh()
                    .build().execute();

            // Set access and refresh token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());

            Console.printout("Token refreshed successfully!", MessageType.INFO);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            Console.printout("Error refreshing token: " + e.getMessage(), MessageType.ERROR);
        }
    }

    public void addSongtoList(String uri) {
        try {
            spotifyApi.addItemToUsersPlaybackQueue(uri).build().execute();
        } catch (Exception e1) {
            Console.printout(e1.getMessage(), MessageType.ERROR);
        }

    }

    public List<IPlaylistItem> getUsersQueue() {
        try {
            List<IPlaylistItem> queue = spotifyApi.getTheUsersQueue().build().execute().getQueue();
            return queue;
        } catch (Exception e1) {
            Console.printout(e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    /**
     * The display name only changes when another account is authenticated, so it is
     * cached instead of being requested again for every admin update.
     */
    public String getUserName() {
        if (cachedUserName != null) {
            return cachedUserName;
        }
        try {
            cachedUserName = spotifyApi.getCurrentUsersProfile().build().execute().getDisplayName();
            return cachedUserName;
        } catch (Exception e1) {
            Console.printout("Error in getUserName: " + e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    public String getURL() {
        try {
            return getCurrentTrackItem().getUri();
        } catch (Exception e1) {
            Console.printError("Error in getURL ", MessageType.ERROR, e1);
            return null;
        }
    }

    /**
     * Always asks Spotify for the current track and updates the cache. Used by the push
     * loop, which is the only caller that needs to notice a change as early as possible.
     */
    public synchronized JSONObject fetchCurrentTrackInfo()
            throws IOException, SpotifyWebApiException, ParseException {
        CurrentlyPlaying currentlyPlaying = spotifyApi.getUsersCurrentlyPlayingTrack().build().execute();
        cacheTime = Instant.now();

        if (currentlyPlaying == null || !(currentlyPlaying.getItem() instanceof Track)) {
            cachedSong = null;
            return null;
        }

        try {
            Track track = (Track) currentlyPlaying.getItem();
            JSONObject trackInfo = new JSONObject();
            trackInfo.put("name", track.getName());
            trackInfo.put("artists", getArtists(track.getArtists()));
            trackInfo.put("albumImageUrl", getAlbumImageUrl(track));
            trackInfo.put("uri", track.getUri());
            cachedSong = trackInfo;
            return trackInfo;
        } catch (NullPointerException e) {
            Console.printError("Error in fetchCurrentTrackInfo ", MessageType.ERROR, e);
            cachedSong = null;
            return null;
        }
    }

    /**
     * Cached view on the current track for requests that a client asked for directly.
     * Refreshes at most once per second so a burst of clients cannot hammer Spotify.
     */
    public synchronized JSONObject getCurrentTrackInfo() throws IOException, SpotifyWebApiException, ParseException {
        if (cacheTime == null || Duration.between(cacheTime, Instant.now()).toMillis() >= CACHE_TTL_MS) {
            return fetchCurrentTrackInfo();
        }
        return cachedSong;
    }

    private String getAlbumImageUrl(Track track) {
        if (track.getAlbum() == null || track.getAlbum().getImages() == null
                || track.getAlbum().getImages().length == 0) {
            return "";
        }
        return track.getAlbum().getImages()[0].getUrl();
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

    public String readCurrentSong() {
        try {
            return getCurrentTrackItem().getName();
        } catch (Exception e1) {
            Console.printout("Error in readCurrentSong: " + e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    public String getAlbumCover() {
        try {
            String trackID = getCurrentTrackItem().getId();
            if (!trackID.equals(currentTrackId)) {
                currentTrackId = trackID;
                TimeUnit.MILLISECONDS.sleep(PAUSE_BETWEEN_REQUESTS_MS); // Pause between requests
                currentAlbumCover = spotifyApi.getTrack(trackID).build().execute().getAlbum().getImages()[0].getUrl();
            }
            return currentAlbumCover;
        } catch (Exception e1) {
            Console.printout("Error in getAlbumCover: " + e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    public ArtistSimplified[] currentSongArtist() {
        try {
            String id = getCurrentTrackItem().getId();
            if (!id.equals(currentTrackId)) {
                currentTrackId = id;
                TimeUnit.MILLISECONDS.sleep(PAUSE_BETWEEN_REQUESTS_MS); // Pause between requests
                currentTrackArtists = spotifyApi.getTrack(id).build().execute().getArtists();
            }
            return currentTrackArtists;
        } catch (Exception e1) {
            Console.printout("Error in currentSongArtist: " + e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    public Track getCurrentTrackItem() throws IOException, SpotifyWebApiException, ParseException {
        IPlaylistItem playlistItem = getCurrentlyPlayingTrack().getItem();
        if (playlistItem == null) {
            return null;
        }
        if (playlistItem instanceof Track) {
            return (Track) playlistItem;
        } else {
            // Handle the case where the item is not a track (e.g., it's an episode)
            return null;
        }
    }

    public CurrentlyPlaying getCurrentlyPlayingTrack() throws IOException, SpotifyWebApiException, ParseException {
        return spotifyApi.getUsersCurrentlyPlayingTrack().build().execute();
    }

    public void songBack() {
        try {
            spotifyApi.skipUsersPlaybackToPreviousTrack().build().execute();
        } catch (Exception e1) {
        }
    }

    public void playPauseSong() {
        try {
            spotifyApi.pauseUsersPlayback().build().execute();
            return;
        } catch (Exception e1) {
        }
        try {
            spotifyApi.startResumeUsersPlayback().build().execute();
            return;
        } catch (Exception e1) {
        }
    }

    public void songVorward() {
        try {
            spotifyApi.skipUsersPlaybackToNextTrack().build().execute();
        } catch (Exception e1) {
        }
    }
}
