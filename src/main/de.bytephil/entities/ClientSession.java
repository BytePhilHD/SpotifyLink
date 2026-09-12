package entities;

import enums.UserType;
import io.javalin.websocket.WsContext;
import lombok.Getter;
import lombok.Setter;

/**
 * State of a single connected websocket client. Kept by the BroadcastService so
 * the server knows who to push updates to and what each client is interested in.
 */
@Getter
@Setter
public class ClientSession {

    private final WsContext ctx;
    private UserType userType;
    /** Session code for users, admin code for admins. Used to drop clients when a new session starts. */
    private String authCode;
    private boolean queueSubscribed;

    public ClientSession(WsContext ctx, UserType userType, String authCode) {
        this.ctx = ctx;
        this.userType = userType;
        this.authCode = authCode;
        this.queueSubscribed = false;
    }
}
