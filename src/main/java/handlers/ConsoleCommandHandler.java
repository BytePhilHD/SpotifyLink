package handlers;

import authorization.SearchRequest;

public class ConsoleCommandHandler {

    public void handleCommand(String command) {
        new SearchRequest().search(command);
    }
}
