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
    /** How often the watchdog looks whether the poll loop is still getting anywhere. */
    private static final long WATCHDOG_INTERVAL_MS = 15000L;
    /** A single poll taking longer than this is stuck on something and is reported. */
    private static final long POLL_STALL_THRESHOLD_MS = 15000L;

    private static final String NOT_PLAYING = "NOT-PLAYING";
    private static final String IDLE = "IDLE";

    private static final Map<String, ClientSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static ScheduledExecutorService scheduler;
    private static ScheduledExecutorService watchdog;

    /** The thread running the poll loop, kept so a stalled poll can be reported with its stack. */
    private static volatile Thread pollThread;
    /** When the poll currently running started, or 0 while no poll is in flight. */
    private static volatile long pollStartedAt;
    private static volatile boolean stallReported;

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
                pollThread = thread;
                return thread;
            }
        });
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                pollStartedAt = System.currentTimeMillis();
                try {
                    poll();
                } catch (Throwable throwable) {
                    // scheduleWithFixedDelay drops the task for good as soon as something
                    // escapes here, and it does so silently. From the outside that looks like
                    // a server that simply stopped updating, so nothing may ever leave.
                    Console.printError("Unexpected error in the push service, the poll loop keeps running",
                            MessageType.ERROR, throwable);
                } finally {
                    pollStartedAt = 0L;
                    stallReported = false;
                }
            }
        }, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Runs on its own thread on purpose: a watchdog sharing the poll thread would sit in
        // the same queue as the poll it is supposed to be watching.
        watchdog = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "spotify-poll-watchdog");
                thread.setDaemon(true);
                return thread;
            }
        });
        watchdog.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                reportStalledPoll();
            }
        }, WATCHDOG_INTERVAL_MS, WATCHDOG_INTERVAL_MS, TimeUnit.MILLISECONDS);

        Console.printout("Push service started (Spotify is polled every " + POLL_INTERVAL_MS + "ms).",
                MessageType.INFO);
    }

    public static void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
    }

    /**
     * The poll loop runs on one thread and holds the Spotify connector while it works, so a
     * single request that never comes back stops the pushes and every directly asked question
     * with them. That is invisible from the outside, so it is named here together with the
     * stack of the thread that is stuck.
     */
    private static void reportStalledPoll() {
        long startedAt = pollStartedAt;
        if (startedAt == 0L || stallReported) {
            return;
        }
        long runningMs = System.currentTimeMillis() - startedAt;
        if (runningMs < POLL_STALL_THRESHOLD_MS) {
            return;
        }
        stallReported = true;
        Console.printout("A Spotify poll has been running for " + (runningMs / 1000)
                + "s and is holding up every update. Stack of the stuck thread:", MessageType.WARNING);
        Thread thread = pollThread;
        if (thread != null) {
            for (StackTraceElement element : thread.getStackTrace()) {
                Console.printout("    at " + element, MessageType.WARNING);
            }
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
        // A rejected token is not worth reporting as long as it can be renewed. Only a
        // refresh that did not work leaves something the operator has to know about.
        if (SpotifyAPIConnector.isAuthError(e) && SpotifyAPIConnector.refreshToken()) {
            lastErrorMessage = null;
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
