package handlers;

import main.Main;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import org.apache.hc.core5.http.ParseException;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class SearchRequest {
    private static final String clientId = Main.config.clientID;
    private static final String clientSecret = Main.config.clientSecret;

    private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .build();
    private static final ClientCredentialsRequest clientCredentialsRequest = spotifyApi.clientCredentials()
            .build();

    public static Paging<Track> searchRequest(String searchrequest) {
        try {
            final ClientCredentials clientCredentials = clientCredentialsRequest.execute();

            // Set access token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(clientCredentials.getAccessToken());

            final Paging<Track> trackPaging = spotifyApi.searchTracks(searchrequest).limit(5).build().execute();

            // TODO mehrere Songs raussuchen und user auswählen lassen welcher der richtige ist

            // TODO Idee: In html den input mit OnInputChangeEvent oder so machen und dann immer live Titel per Websocket senden

            return trackPaging;
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }


    public static void clientCredentials_Sync(String searchrequest) {
        try {
            final ClientCredentials clientCredentials = clientCredentialsRequest.execute();

            // Set access token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(clientCredentials.getAccessToken());

            final Paging<Track> trackPaging = spotifyApi.searchTracks(searchrequest).limit(1).build().execute();

            String answer = trackPaging.toString();

            int iend = answer.indexOf("id=");
            String id = answer.substring(iend+3, iend+25);

            System.out.println("ID: " + id);
            System.out.println(answer);

            System.out.println("Expires in: " + clientCredentials.getExpiresIn());
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void clientCredentials_Async() {
        try {
            final CompletableFuture<ClientCredentials> clientCredentialsFuture = clientCredentialsRequest.executeAsync();

            // Thread free to do other tasks...

            // Example Only. Never block in production code.
            final ClientCredentials clientCredentials = clientCredentialsFuture.join();

            // Set access token for further "spotifyApi" object usage
            spotifyApi.setAccessToken(clientCredentials.getAccessToken());

            System.out.println("Expires in: " + clientCredentials.getExpiresIn());
        } catch (CompletionException e) {
            System.out.println("Error: " + e.getCause().getMessage());
        } catch (CancellationException e) {
            System.out.println("Async operation cancelled.");
        }
    }

    public void search(String searchRequest) {
        clientCredentials_Sync(searchRequest);
    }

    public static void main(String[] args) {
        clientCredentials_Sync("MArtin GArrix");
        clientCredentials_Async();
    }
}
