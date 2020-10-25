package actors.utils;

import java.time.Instant;
import java.util.logging.Level;

public class Logger {
    public Logger() {}

    public void info(String message) {
        Instant nowDate = Instant.now();
        String nowString = nowDate.toString().replace("T", " ").replace("Z", "");
        System.out.println("[" + nowString + "] " + "[" + Level.INFO + "] - " + message);
    }
}
