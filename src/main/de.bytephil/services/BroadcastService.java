package services;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import authorization.SpotifyAPIConnector;
import entities.ClientSession;
import entities.SongObject;
import enums.MessageType;
import enums.UserType;
import io.javalin.websocket.WsContext;
import main.Main;

/**
 * Polls Spotify once per second on behalf of every connected client and pushes an
 * update only when something actually changed. Clients therefore no longer have to
 * ask for the current state on a timer.
 */
public final class BroadcastService {

    /** How often the server asks Spotify for the current state. */
    private static final long POLL_INTERVAL_MS = 1000L;
    /** Small delay before an extra poll triggered by an admin action, so Spotify has applied it. */
    private static final long POKE_DELAY_MS = 250L;

    private static final String NOT_PLAYING = "NOT-PLAYING";
    private static final String IDLE = "IDLE";

    private static final Map<String, ClientSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static ScheduledExecutorService scheduler;

    private static String lastTrackSignature;
    private static String lastQueueSignature;
    private static String lastErrorMessage;

    private BroadcastService() {
    }

    public static void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "spotify-poller");
                thread.setDaemon(true);
                return thread;
            }
        });
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                poll();
            }
        }, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        Console.printout("Push service started (Spotify is polled every " + POLL_INTERVAL_MS + "ms).",
                MessageType.INFO);
    }

    public static void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Remembers a client so it receives pushes. Called for every valid message, so an
     * already known client keeps its queue subscription and only gets its type refreshed.
     */
    public static void register(WsContext ctx, UserType userType, String authCode) {
        ClientSession session = SESSIONS.get(ctx.sessionId());
        if (session == null) {
            SESSIONS.put(ctx.sessionId(), new ClientSession(ctx, userType, authCode));
        } else {
            session.setUserType(userType);
            session.setAuthCode(authCode);
        }
    }

    public static void unregister(String sessionId) {
        SESSIONS.remove(sessionId);
    }

    public static void setQueueSubscribed(String sessionId, boolean subscribed) {
        ClientSession session = SESSIONS.get(sessionId);
        if (session != null) {
            session.setQueueSubscribed(subscribed);
        }
    }

    public static boolean isQueueSubscribed(String sessionId) {
        ClientSession session = SESSIONS.get(sessionId);
        return session != null && session.isQueueSubscribed();
    }

    /** Makes the next poll push again even if Spotify reports an unchanged state. */
    public static void invalidate() {
        lastTrackSignature = null;
        lastQueueSignature = null;
    }

    /** Runs an extra poll shortly from now, used after an admin changed the playback. */
    public static void pokeNow() {
        if (scheduler == null) {
            return;
        }
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                poll();
            }
        }, POKE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Disconnects every user that authenticated with an outdated session code.
     * Admins keep their connection and receive the new code with the next push.
     */
    public static void dropOutdatedUsers() {
        Iterator<Map.Entry<String, ClientSession>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            ClientSession session = iterator.next().getValue();
            if (session.getUserType() == UserType.USER && !Main.sessionCode.equals(session.getAuthCode())) {
                sendTo(session, "forbidden");
                iterator.remove();
            }
        }
    }

    /** Tells every client that the requested songs changed, so open search results can be redrawn. */
    public static void broadcastPlayedUpdate() {
        JSONObject payload = new JSONObject();
        payload.put("type", "played-update");
        String message = payload.toString();
        for (ClientSession session : SESSIONS.values()) {
            sendTo(session, message);
        }
    }

    public static JSONObject buildQueuePayload(List<SongObject> queue) {
        JSONObject payload = new JSONObject();
        payload.put("type", "queue");
        try {
            List<SongObject> songs = (queue == null) ? new ArrayList<SongObject>() : queue;
            payload.put("results", new JSONArray(OBJECT_MAPPER.writeValueAsString(songs)));
        } catch (Exception e) {
            payload.put("results", new JSONArray());
        }
        return payload;
    }

    /**
     * Builds the state message for one recipient. The cached track object is never
     * modified here, otherwise admin only fields would leak to regular users.
     */
    public static JSONObject buildStatePayload(JSONObject trackInfo, UserType userType) {
        JSONObject payload = (trackInfo == null)
                ? new JSONObject().put("Not-playing", true)
                : new JSONObject(trackInfo.toString());

        if (userType == UserType.ADMIN) {
            String userName = Main.spotifyConnector.getUserName();
            payload.put("user", userName == null ? "Unbekannt" : userName);
            payload.put("sessionCode", Main.sessionCode);
        } else {
            payload.put("user", "Unbekannt");
        }
        return payload;
    }

    private static void poll() {
        if (SESSIONS.isEmpty()) {
            return;
        }
        try {
            if (!Main.isPlaybackActive()) {
                if (!IDLE.equals(lastTrackSignature)) {
                    lastTrackSignature = IDLE;
                    lastQueueSignature = null;
                    broadcastState(null);
                }
                return;
            }

            JSONObject trackInfo = Main.spotifyConnector.fetchCurrentTrackInfo();
            String trackSignature = (trackInfo == null) ? NOT_PLAYING : trackInfo.optString("uri", NOT_PLAYING);
            if (!trackSignature.equals(lastTrackSignature)) {
                lastTrackSignature = trackSignature;
                broadcastState(trackInfo);
            }

            pollQueue();
            lastErrorMessage = null;
        } catch (Exception e) {
            handlePollError(e);
        }
    }

    /** The queue costs an extra Spotify request, so it is only polled while somebody looks at it. */
    private static void pollQueue() {
        if (!hasQueueSubscribers()) {
            lastQueueSignature = null;
            return;
        }
        List<SongObject> queue = Main.getSpotifyHandler().refreshQueue();
        String queueSignature = queueSignature(queue);
        if (!queueSignature.equals(lastQueueSignature)) {
            lastQueueSignature = queueSignature;
            String message = buildQueuePayload(queue).toString();
            for (ClientSession session : SESSIONS.values()) {
                if (session.isQueueSubscribed()) {
                    sendTo(session, message);
                }
            }
        }
    }

    private static void broadcastState(JSONObject trackInfo) {
        String userMessage = buildStatePayload(trackInfo, UserType.USER).toString();
        String adminMessage = null;

        for (ClientSession session : SESSIONS.values()) {
            if (session.getUserType() == UserType.ADMIN) {
                if (adminMessage == null) {
                    adminMessage = buildStatePayload(trackInfo, UserType.ADMIN).toString();
                }
                sendTo(session, adminMessage);
            } else {
                sendTo(session, userMessage);
            }
        }
    }

    private static boolean hasQueueSubscribers() {
        for (ClientSession session : SESSIONS.values()) {
            if (session.isQueueSubscribed()) {
                return true;
            }
        }
        return false;
    }

    private static String queueSignature(List<SongObject> queue) {
        if (queue == null || queue.isEmpty()) {
            return "";
        }
        StringBuilder signature = new StringBuilder();
        for (SongObject song : queue) {
            signature.append(song.getUri()).append(",");
        }
        return signature.toString();
    }

    /**
     * Sends to a client that may not be registered yet. Every message going to a client
     * of the main endpoint has to take this path, see {@link #sendTo(ClientSession, String)}.
     */
    public static void sendTo(WsContext ctx, String message) {
        ClientSession session = SESSIONS.get(ctx.sessionId());
        if (session == null) {
            ctx.send(message);
            return;
        }
        sendTo(session, message);
    }

    /**
     * Javalin writes with Jettys blocking sendString, which must not be called for the
     * same connection from two threads at once. The push loop and the thread handling an
     * incoming message do exactly that, so writes are serialized per client here.
     */
    private static void sendTo(ClientSession session, String message) {
        try {
            synchronized (session) {
                if (!session.getCtx().session.isOpen()) {
                    SESSIONS.remove(session.getCtx().sessionId());
                    return;
                }
                session.getCtx().send(message);
            }
        } catch (Exception e) {
            // Usually a connection that went away between the check and the write.
            // Dropping it here is only correct once it is really closed, a failed write
            // on a still open connection must not cost the client its updates.
            if (!session.getCtx().session.isOpen()) {
                SESSIONS.remove(session.getCtx().sessionId());
            }
        }
    }

    private static void handlePollError(Exception e) {
        String message = e.getMessage();
        if (message != null && message.contains("The access token expired")) {
            SpotifyAPIConnector.refreshToken();
            return;
        }
        // The poll runs every second, so the same failure must not flood the console.
        if (message != null && message.equals(lastErrorMessage)) {
            return;
        }
        lastErrorMessage = message;
        Console.printout("Error while polling Spotify: " + message, MessageType.ERROR);
    }
}
