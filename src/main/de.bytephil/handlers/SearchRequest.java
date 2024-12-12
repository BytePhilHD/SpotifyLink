package handlers;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.core5.http.ParseException;

import authorization.SpotifyAPIConnector;
import enums.MessageType;
import main.Main;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import se.michaelthelin.spotify.requests.data.tracks.GetSeveralTracksRequest;
import se.michaelthelin.spotify.requests.data.tracks.GetTrackRequest;
import services.Console;

public class SearchRequest {
    private static final String CLIENT_ID = Main.config.clientID;
    private static final String CLIENT_SECRET = Main.config.clientSecret;

    private static final SpotifyApi spotifyApi = SpotifyAPIConnector.spotifyApi;
    /*
     * private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
     * .setClientId(CLIENT_ID)
     * .setClientSecret(CLIENT_SECRET)
     * .build();
     */
    private static final ClientCredentialsRequest clientCredentialsRequest = spotifyApi.clientCredentials()
            .build();

    private static String accessToken;
    private static Instant tokenExpirationTime;

    private static void refreshAccessToken() {
        try {
            final ClientCredentials clientCredentials = clientCredentialsRequest.execute();
            accessToken = clientCredentials.getAccessToken();
            tokenExpirationTime = Instant.now().plusSeconds(clientCredentials.getExpiresIn());
            spotifyApi.setAccessToken(accessToken);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            Console.printError("Error refreshing access token", MessageType.ERROR, e);
        }
    }

    private static void ensureAccessToken() {
        if (accessToken == null || Instant.now().isAfter(tokenExpirationTime)) {
            refreshAccessToken();
        }
    }

    public static Track getTrackById(String uri) {
        ensureAccessToken();
        GetTrackRequest getTrackRequest = spotifyApi.getTrack(uri).build();
        try {
            return getTrackRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            Console.printError("Error at SearchRequest", MessageType.ERROR, e);
        }
        return null;
    }

    public static Track[] getSeveralTracks_Sync(String[] ids) {
        ensureAccessToken();

        GetSeveralTracksRequest getSeveralTracksRequest = spotifyApi.getSeveralTracks(ids).build();
        try {
            final Track[] tracks = getSeveralTracksRequest.execute();

            return tracks;
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            Console.printError("Error at SearchRequest", MessageType.ERROR, e);
            return null;
        }
    }

    public static Paging<Track> searchRequest(String searchrequest) {
        ensureAccessToken();
        try {
            return spotifyApi.searchTracks(searchrequest).limit(3).build().execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            Console.printError("Error at SearchRequest", MessageType.ERROR, e);
        }
        return null;
    }

    public static void clientCredentials_Sync(String searchrequest) {
        ensureAccessToken();
        try {
            final Paging<Track> trackPaging = spotifyApi.searchTracks(searchrequest).limit(1).build().execute();

            String answer = trackPaging.toString();

            int iend = answer.indexOf("id=");
            String id = answer.substring(iend + 3, iend + 25);

            System.out.println("ID: " + id);
            System.out.println(answer);

            System.out.println("Expires in: " + tokenExpirationTime);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            Console.printError("Error at SearchRequest", MessageType.ERROR, e);
        }
    }

    public static void clientCredentials_Async() {
        try {
            final CompletableFuture<ClientCredentials> clientCredentialsFuture = clientCredentialsRequest
                    .executeAsync();

            // Thread free to do other tasks...

            // Example Only. Never block in production code.
            final ClientCredentials clientCredentials = clientCredentialsFuture.join();

            // Set access token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(clientCredentials.getAccessToken());

            System.out.println("Expires in: " + clientCredentials.getExpiresIn());
        } catch (Exception e) {
            Console.printError("Error at SearchRequest", MessageType.ERROR, e);
        }
    }
}