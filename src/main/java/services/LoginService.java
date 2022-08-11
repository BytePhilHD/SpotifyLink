package services;

import main.Main;

import java.util.HashMap;

public class LoginService {

    public static HashMap<String, String> loggedinUsers = new HashMap<>();

    public static boolean login(String webSocketAnswer, String sessionID) {
        String pw = webSocketAnswer.replace("PW: ", "");
        String passwordADMIN = Main.config.password;

        if (pw.equals(passwordADMIN)) {
            return true;
        } else {
            return false;
        }
    }
}
