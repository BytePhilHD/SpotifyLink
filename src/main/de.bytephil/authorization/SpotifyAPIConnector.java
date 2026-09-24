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
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
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
    /**
     * Spotify hands out an access token that is valid for one hour. It is renewed this much
     * earlier, so a request can never race the expiry.
     */
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 120;
    /** How long to wait after a failed refresh, so a token that cannot be renewed does not flood the console. */
    private static final long REFRESH_RETRY_DELAY_MS = 30000L;
    /**
     * Apache HttpClient waits for an answer indefinitely by default and only the socket gives
     * up, after three minutes. The poll loop holds this object while a request runs, so such a
     * request freezes both the pushes and every client that asks directly. Ten seconds is far
     * more than Spotify ever needs.
     */
    private static final int REQUEST_TIMEOUT_MS = 10000;

    private Instant cacheTime;
    private JSONObject cachedSong;
    private static String cachedUserName;

    /** When the current access token stops working. Null until something was authenticated. */
    private static Instant tokenExpiry;
    private static Instant lastFailedRefresh;

    private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRedirectUri(redirectUri)
            .setHttpManager(new SpotifyHttpManager.Builder()
                    .setConnectionRequestTimeout(REQUEST_TIMEOUT_MS)
                    .setSocketTimeout(REQUEST_TIMEOUT_MS)
                    .build())
            .build();

    private String currentTrackId = null;
    private ArtistSimplified[] currentTrackArtists = null;
    private String currentAlbumCover = null;

    public static void authorizationCode_Sync(String code1) {
        try {
            final AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(code1)
                    .build().execute();

            // Set access and refresh token for further "spotifyApi" object usage
            rememberCredentials(authorizationCodeCredentials);

            // A different account may have been authenticated, so the cached name is stale.
            cachedUserName = null;

            Console.printout("Authentication successful!", MessageType.INFO);
            Main.setStartingUp(false);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            if (e.getMessage() != null && e.getMessage().contains("Authorization code expired")) {
                refreshToken();
            } else {
                Console.printError("Error at SpotifyAPIConnector", MessageType.ERROR, e);
            }
        }
    }

    /**
     * Stores a freshly issued token together with the moment it stops working. Spotify only
     * sends a new refresh token now and then, so the old one is kept when none came back.
     */
    private static synchronized void rememberCredentials(AuthorizationCodeCredentials credentials) {
        spotifyApi.setAccessToken(credentials.getAccessToken());
        if (credentials.getRefreshToken() != null) {
            spotifyApi.setRefreshToken(credentials.getRefreshToken());
        }
        Main.refreshToken = spotifyApi.getRefreshToken();
        tokenExpiry = (credentials.getExpiresIn() == null)
                ? null
                : Instant.now().plusSeconds(credentials.getExpiresIn());
        lastFailedRefresh = null;
    }

    /** True while there is no usable token, or while the one at hand is about to run out. */
    private static synchronized boolean tokenNeedsRefresh() {
        if (spotifyApi.getAccessToken() == null) {
            return true;
        }
        return tokenExpiry == null
                || !Instant.now().isBefore(tokenExpiry.minusSeconds(TOKEN_REFRESH_MARGIN_SECONDS));
    }

    /**
     * Renews the access token. Returns whether a usable token is available afterwards, so a
     * caller can tell a rejection it can recover from apart from one that needs a new login.
     */
    public static synchronized boolean refreshToken() {
        if (lastFailedRefresh != null
                && Duration.between(lastFailedRefresh, Instant.now()).toMillis() < REFRESH_RETRY_DELAY_MS) {
            // A refresh that just failed will not succeed a second later, and the poll runs
            // every second, so the next attempt is held back instead.
            return false;
        }
        if (spotifyApi.getRefreshToken() == null) {
            // Happens while the server is up but nobody authenticated yet. The cooldown above
            // keeps that from being reported over and over.
            lastFailedRefresh = Instant.now();
            Console.printout("Cannot refresh the Spotify token, no account is authenticated.", MessageType.WARNING);
            return false;
        }
        try {
            final AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCodeRefresh()
                    .build().execute();

            rememberCredentials(authorizationCodeCredentials);

            Console.printout("Token refreshed successfully!", MessageType.INFO);
            return true;
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            lastFailedRefresh = Instant.now();
            Console.printout("Error refreshing token: " + e.getMessage(), MessageType.ERROR);
            return false;
        } catch (RuntimeException e) {
            // The HTTP and JSON layers below can throw unchecked. Letting that through would
            // reach the scheduled poll task and kill it for good, without a word in the log.
            lastFailedRefresh = Instant.now();
            Console.printError("Unexpected error refreshing token", MessageType.ERROR, e);
            return false;
        }
    }

    /**
     * Whether Spotify refused a request because of the token. The wording differs per endpoint
     * ("The access token expired", "Missing/invalid/expired access token"), so the rejection
     * itself is what counts here, not the text that came with it.
     */
    public static boolean isAuthError(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof UnauthorizedException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("access token")) {
                return true;
            }
        }
        return false;
    }

    private interface SpotifyCall<T> {
        T execute() throws IOException, SpotifyWebApiException, ParseException;
    }

    /**
     * Runs a request with a token that is known to be valid. The token is renewed before it
     * expires and, should Spotify reject it anyway, once more right after the rejection.
     * Without this, every request failed roughly an hour after the authentication and only a
     * restart of the server brought the connection back.
     */
    private static <T> T call(SpotifyCall<T> request) throws IOException, SpotifyWebApiException, ParseException {
        if (tokenNeedsRefresh()) {
            refreshToken();
        }
        try {
            return request.execute();
        } catch (UnauthorizedException e) {
            if (!refreshToken()) {
                throw e;
            }
            return request.execute();
        }
    }

    public void addSongtoList(String uri) {
        try {
            call(() -> spotifyApi.addItemToUsersPlaybackQueue(uri).build().execute());
        } catch (Exception e1) {
            Console.printout(e1.getMessage(), MessageType.ERROR);
        }

    }

    public List<IPlaylistItem> getUsersQueue() {
        try {
            return call(() -> spotifyApi.getTheUsersQueue().build().execute().getQueue());
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
            cachedUserName = call(() -> spotifyApi.getCurrentUsersProfile().build().execute().getDisplayName());
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
        CurrentlyPlaying currentlyPlaying = getCurrentlyPlayingTrack();
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
            final String trackID = getCurrentTrackItem().getId();
            if (!trackID.equals(currentTrackId)) {
                currentTrackId = trackID;
                TimeUnit.MILLISECONDS.sleep(PAUSE_BETWEEN_REQUESTS_MS); // Pause between requests
                currentAlbumCover = call(
                        () -> spotifyApi.getTrack(trackID).build().execute().getAlbum().getImages()[0].getUrl());
            }
            return currentAlbumCover;
        } catch (Exception e1) {
            Console.printout("Error in getAlbumCover: " + e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    public ArtistSimplified[] currentSongArtist() {
        try {
            final String id = getCurrentTrackItem().getId();
            if (!id.equals(currentTrackId)) {
                currentTrackId = id;
                TimeUnit.MILLISECONDS.sleep(PAUSE_BETWEEN_REQUESTS_MS); // Pause between requests
                currentTrackArtists = call(() -> spotifyApi.getTrack(id).build().execute().getArtists());
            }
            return currentTrackArtists;
        } catch (Exception e1) {
            Console.printout("Error in currentSongArtist: " + e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    public Track getCurrentTrackItem() throws IOException, SpotifyWebApiException, ParseException {
        CurrentlyPlaying currentlyPlaying = getCurrentlyPlayingTrack();
        if (currentlyPlaying == null) {
            return null;
        }
        IPlaylistItem playlistItem = currentlyPlaying.getItem();
        if (playlistItem == null) {
            return null;
        }
        if (playlistItem instanceof Track) {
            return (Track) playlistItem;
        } else {
            // Handle the case where the item is not a track (e.g. an episode)
            return null;
        }
    }

    public CurrentlyPlaying getCurrentlyPlayingTrack() throws IOException, SpotifyWebApiException, ParseException {
        return call(() -> spotifyApi.getUsersCurrentlyPlayingTrack().build().execute());
    }

    public void songBack() {
        try {
            call(() -> spotifyApi.skipUsersPlaybackToPreviousTrack().build().execute());
        } catch (Exception e1) {
        }
    }

    public void playPauseSong() {
        try {
            call(() -> spotifyApi.pauseUsersPlayback().build().execute());
            return;
        } catch (Exception e1) {
        }
        try {
            call(() -> spotifyApi.startResumeUsersPlayback().build().execute());
            return;
        } catch (Exception e1) {
        }
    }

    public void songVorward() {
        try {
            call(() -> spotifyApi.skipUsersPlaybackToNextTrack().build().execute());
        } catch (Exception e1) {
        }
    }
}
