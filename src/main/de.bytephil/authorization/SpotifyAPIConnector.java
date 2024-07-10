package authorization;

import enums.MessageType;
import main.Main;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import org.apache.hc.core5.http.ParseException;
import org.json.JSONObject;

import services.Console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class SpotifyAPIConnector {
    private static final String clientId = Main.config.clientID;
    private static final String clientSecret = Main.config.clientSecret;
    private static final URI redirectUri = SpotifyHttpManager.makeUri(Main.config.webaddress + "auth.html");
    public static String code = "";
    private static final long PAUSE_BETWEEN_REQUESTS_MS = 200; 

    private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRedirectUri(redirectUri)
            .build();

    private String currentTrackId = null;
    private ArtistSimplified[] currentTrackArtists = null;
    private String currentAlbumCover = null;

    public static void authorizationCode_Sync(String code1) {
        try {
            final AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(code1).build().execute();
    
            // Set access and refresh token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
    
            Console.printout("Authentication successful!", MessageType.INFO);
            Console.printout(authorizationCodeCredentials.getAccessToken(), MessageType.INFO);
            reader();
    
            System.out.println("Expires in: " + authorizationCodeCredentials.getExpiresIn());
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            if (e.getMessage().contains("Authorization code expired")) {
                refreshToken();
            } else {
                Console.printout("Error at SpotifyAPIConnector: " + e.getMessage(), MessageType.ERROR);
            }
        }
    }
    
    public static void refreshToken() {
        try {
            final AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCodeRefresh().build().execute();
    
            // Set access and refresh token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
    
            Console.printout("Token refreshed successfully!", MessageType.INFO);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            Console.printout("Error refreshing token: " + e.getMessage(), MessageType.ERROR);
        }
    }
    

    public static void reader() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String input = reader.readLine();
            // ...
        } catch (IOException e1) {
            Console.printout("Error in reader: " + e1.getMessage(), MessageType.ERROR);
        } 
    }

    public void addSongtoList(String uri) {
        try {
            spotifyApi.addItemToUsersPlaybackQueue(uri).build().execute();
        } catch (Exception e1) {
            Console.printout(e1.getMessage(), MessageType.ERROR);
        }

    }


    public String getURL() {
        try {
            return getCurrentTrackItem().getUri();
        } catch (Exception e1) {
            Console.printout("Error in getURL: " + e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    public JSONObject getCurrentTrackInfo() throws IOException, SpotifyWebApiException, ParseException {
        IPlaylistItem playlistItem = spotifyApi.getUsersCurrentlyPlayingTrack().build().execute().getItem();
        if (playlistItem instanceof Track) {
            Track track = (Track) playlistItem;
            JSONObject trackInfo = new JSONObject();
            trackInfo.put("name", track.getName());
            trackInfo.put("artists", getArtists(track.getArtists()));
            trackInfo.put("cover", track.getAlbum().getImages()[0].getUrl());
            trackInfo.put("uri", track.getUri());
            return trackInfo;
        } else {
            // Handle the case where the item is not a track (e.g., it's an episode)
            return null;
        }
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
                TimeUnit.MILLISECONDS.sleep(PAUSE_BETWEEN_REQUESTS_MS);  // Pause between requests
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
                TimeUnit.MILLISECONDS.sleep(PAUSE_BETWEEN_REQUESTS_MS);  // Pause between requests
                currentTrackArtists = spotifyApi.getTrack(id).build().execute().getArtists();
            }
            return currentTrackArtists;
        } catch (Exception e1) {
            Console.printout("Error in currentSongArtist: " + e1.getMessage(), MessageType.ERROR);
            return null;
        }
    }

    public Track getCurrentTrackItem() throws IOException, SpotifyWebApiException, ParseException {
        IPlaylistItem playlistItem = spotifyApi.getUsersCurrentlyPlayingTrack().build().execute().getItem();
        if (playlistItem instanceof Track) {
            return (Track) playlistItem;
        } else {
            // Handle the case where the item is not a track (e.g., it's an episode)
            return null;
        }
    }
}
