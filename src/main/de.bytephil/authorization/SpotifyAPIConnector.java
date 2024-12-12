package authorization;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ParseException;

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

    public static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
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

            Console.printout("Authentication successful!", MessageType.INFO);
            Console.printout(authorizationCodeCredentials.getAccessToken(), MessageType.INFO);
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

    public String getUserName() {
        try {
            return spotifyApi.getCurrentUsersProfile().build().execute().getDisplayName();
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
        CurrentlyPlaying currentlyPlaying = spotifyApi.getUsersCurrentlyPlayingTrack().build().execute();
        if (currentlyPlaying == null) {
            return null;
        }
        IPlaylistItem playlistItem = currentlyPlaying.getItem();
        if (playlistItem instanceof Track) {
            return (Track) playlistItem;
        } else {
            // Handle the case where the item is not a track (e.g., it's an episode)
            return null;
        }
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
