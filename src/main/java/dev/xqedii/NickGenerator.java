package dev.xqedii;

import java.io.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class NickGenerator {

    private static final int NICK_LEN = 16;
    private final SecureRandom random = new SecureRandom();
    private final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final int charsLen = 62;
    private int linesSize;
    private List<String> lines;
    private int nextNickIndex = 0;
    private int nickLen = NICK_LEN;
    private boolean real = false;
    private String prefix = "";

    public int loadFromFile(String filePath) {
        lines = new ArrayList<>();
        try {
            Scanner scanner = new Scanner(new File(filePath));
            while (scanner.hasNextLine()) {
                try {
                    String line = scanner.nextLine().trim();
                    if (line.matches("^[a-zA-Z0-9_]{3,16}$")) {
                        lines.add(line);
                    }
                } catch (Exception ignored) {
                }
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            Log.error("Invalid nicknames list file path");
            System.exit(1);
        }

        linesSize = lines.size();
        Log.info("Loaded " + linesSize + " valid nicknames");
        real = true;

        return linesSize;
    }

    private void loadLines() {
        try (InputStream resource = getClass().getResourceAsStream("/files/nicks.txt")) {
            lines = new BufferedReader(
                    new InputStreamReader(resource)).lines().collect(Collectors.toList()
            );
            linesSize = lines.size();
        } catch (Exception e) {
            Log.error(e);
            System.exit(1);
        }
    }

    public String generateRandom(int len) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < len; i++) {
            result.append(CHARS.charAt(random.nextInt(charsLen)));
        }
        return result.toString();
    }

    public String nextRandom() {
        return prefix + generateRandom(nickLen);
    }

    public String nextReal() {
        if (linesSize == 0) {
            return nextRandom();
        }

        String nick = prefix + lines.get(nextNickIndex);

        nextNickIndex++;

        if (nextNickIndex >= linesSize) {
            nextNickIndex = 0;
        }

        return nick.length() <= 16 ? nick : nick.substring(0, 15);
    }

    public String nextNick() {
        return real ? nextReal() : nextRandom();
    }

    public void setPrefix(String prefix) {
        nickLen = NICK_LEN - prefix.length();
        this.prefix = prefix;
    }

    public void setReal(boolean real) {
        if (real && lines == null) loadLines();
        this.real = real;
    }
}