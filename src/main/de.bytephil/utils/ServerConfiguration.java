package utils;

import enums.MessageType;
import services.Console;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfiguration extends Config {
    public boolean loaded = true;

    public ServerConfiguration(String path) {
        Properties prop = new Properties();
        String fileName = "server.cfg";
        InputStream is = null;
        try {
            is = new FileInputStream(fileName);
        } catch (FileNotFoundException ex) {
            Console.printout("The config file is missing!", MessageType.WARNING);
            loaded = false;
        }
        try {
            prop.load(is);
        } catch (Exception ex) {
            Console.printout("Couldn't read config file", MessageType.WARNING);
            loaded = false;
        }
        clientID= prop.getProperty("clientID", "yourClientID");
        clientSecret = prop.getProperty("clientSecret", "yourClientSecret");


        port = Integer.parseInt(prop.getProperty("http.port", "80"));
        password = prop.getProperty("password", "YourPW!");
        username = prop.getProperty("adminusername", "admin");
        debugMSG = Boolean.parseBoolean(prop.getProperty("debugMSG", "false"));
        address = prop.getProperty("webaddress", "https://bytephil.de/");

        autoUpdate = Boolean.parseBoolean(prop.getProperty("app.autoUpdate", "true"));
        http = Boolean.parseBoolean(prop.getProperty("http.activated", "true"));
        https = Boolean.parseBoolean(prop.getProperty("ssl.activated", "false"));
        sslPort = Integer.parseInt(prop.getProperty("ssl.port", "443"));
        keystorePath = prop.getProperty("ssl.keystorePath", "keystore.jks");
        keystorePW = prop.getProperty("ssl.keystorePassword", "password");

        // EMAIL
        emailhost = prop.getProperty("email.smtp.host", "smtp.gmail.com");
        emailport = Integer.parseInt(prop.getProperty("email.smtp.port", "587"));
        emailSecureMethod = prop.getProperty("email.smtp.secure.method", "STARTTLS");
        emailDisplayName = prop.getProperty("email.smtp.displayname", "");
        emailuser = prop.getProperty("email.smtp.user", "testuser@gmail.com");
        emailpassword = prop.getProperty("email.smtp.password", "yourPassword");
    }
}