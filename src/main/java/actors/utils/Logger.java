package actors.utils;

import java.time.Instant;
import java.util.logging.Level;

public class Logger {

    private boolean logAll;

    public Logger(boolean all) {
        this.logAll = all;
    }

    public void important(String message) {
        log(message);
    }

    public void info(String message) {
        if (logAll) {
            log(message);
        }
    }

    private void log(String message) {
        Instant nowDate = Instant.now();
        String nowString = nowDate.toString().replace("T", " ").replace("Z", "");
        System.out.println("[" + nowString + "] " + "[" + Level.INFO + "] - " + message);
    }
}
