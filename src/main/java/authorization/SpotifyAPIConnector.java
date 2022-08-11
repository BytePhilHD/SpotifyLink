package authorization;

import enums.MessageType;
import main.Main;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import org.apache.hc.core5.http.ParseException;
import services.Console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;

/*

        API from https://github.com/spotify-web-api-java
 */

public class SpotifyAPIConnector {
    private static final String clientId = Main.config.clientID;
    private static final String clientSecret = Main.config.clientSecret;
    private static final URI redirectUri = SpotifyHttpManager.makeUri("http://localhost/auth.html");
    public static String code = "";

    private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRedirectUri(redirectUri)
            .build();
    private static final AuthorizationCodeRequest authorizationCodeRequest = spotifyApi.authorizationCode(code)
            .build();

    public static void authorizationCode_Sync(String code1) {
        try {
            final AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(code1).build().execute();

            // Set access and refresh token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

            Console.printout("Authentication successful!", MessageType.INFO);
            reader();

            System.out.println("Expires in: " + authorizationCodeCredentials.getExpiresIn());
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Error at SpotifyAPIConnector: " + e.getMessage());
        }
    }

    public static void reader() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String input = null;
        try {
            input = reader.readLine();
            if (input.equalsIgnoreCase("play")) {
                System.out.println("PLAY");
                spotifyApi.startResumeUsersPlayback().build().execute();
                reader();
            } else if (input.equalsIgnoreCase("pause")) {
                System.out.println("PAUSE");
                spotifyApi.pauseUsersPlayback().build().execute();
                reader();
            } else if (input.equalsIgnoreCase("read")) {
                System.out.println("READ: " + spotifyApi.getUsersCurrentlyPlayingTrack().build().execute().getItem().getName());
                reader();
            } else if (input.contains("search")) {
                input.replace("search", "");

               // spotifyApi.addItemToUsersPlaybackQueue()
            }
        } catch (IOException e1) {} catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (SpotifyWebApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void addSongtoList(String uri) {
        try {
            spotifyApi.addItemToUsersPlaybackQueue(uri).build().execute();
            //spotifyApi.skipUsersPlaybackToNextTrack().build().execute();
        } catch (Exception e1) {
            Console.printout(e1.getMessage(), MessageType.ERROR);
        }

    }

    public String readCurrentSong() {
        try {
            return spotifyApi.getUsersCurrentlyPlayingTrack().build().execute().getItem().getName();
        } catch (Exception e1) {}
        return null;
    }

    public String getAlbumCover() {
        try {
            String trackID = spotifyApi.getUsersCurrentlyPlayingTrack().build().execute().getItem().getId();
            return spotifyApi.getTrack(trackID).build().execute().getAlbum().getImages()[0].getUrl();
        } catch (Exception e1) {}
        return null;
    }


    public ArtistSimplified[] currentSongArtist() {
        try {
            String id = spotifyApi.getUsersCurrentlyPlayingTrack().build().execute().getItem().getId();
            return spotifyApi.getTrack(id).build().execute().getArtists();
        } catch (Exception e1) {}

        return null;
    }

}