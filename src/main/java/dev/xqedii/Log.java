package dev.xqedii;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {

    public static void log(String in) {
        System.out.println(in);
    }

    public static void error(String in) {
        log("ERROR | " + in);
    }

    public static void error(Exception e) {
        log("ERROR | ");
        e.printStackTrace();
    }

    public static void info(String ...in) {
        log("INFO | " + String.join(" ", in));
    }
    public static void info() {
        log("INFO | ");
    }

    public static void imp(String ...in) {
        log("IMP | " + String.join(" ", in));
    }
    public static void error(String ...in) {
        log("Error | " + String.join(" ", in));
    }
    public static void imp() {
        log("IMP | ");
    }

    public static void warn(String ...in) {
        log("WARN | " + String.join(" ", in));
    }
    public static void warn() {
        log("WARN | ");
    }

    public static void chat(String ...in) {
        log("CHAT | " + String.join(" ", in));
    }

    public static void crit(Exception e) {
        error(e);
        System.exit(1);
    }
}