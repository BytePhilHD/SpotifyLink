package services;

import java.util.HashMap;

import main.Main;

public class LoginService {

    public static HashMap<String, String> loggedinUsers = new HashMap<>();

    public static boolean login(String webSocketAnswer, String sessionID) {
        String pw = webSocketAnswer.replace("LOGIN: ", "");
        String passwordADMIN = Main.config.password;

        return pw.equals(passwordADMIN);
    }
}
