package dev.xqedii;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListenerManager {

    private final Map<ListenerType, List<ListenerRule>> listeners = new EnumMap<>(ListenerType.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("\\b[a-zA-Z0-9]{5,10}\\b");
    private static final Pattern PLAYER_CHAT_PATTERN = Pattern.compile("^(?:<|\\[[^\\]]+] )?(\\w{3,16})(?:>|:| >>) (.+)");

    public ListenerManager(String folderPath) {
        for (ListenerType type : ListenerType.values()) {
            listeners.put(type, new ArrayList<>());
        }
        loadListenersFromFolder(folderPath);
    }

    private void loadListenersFromFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            Log.warn("Folder z listenerami nie istnieje lub nie jest folderem: " + folderPath);
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null || files.length == 0) {
            Log.info("Nie znaleziono plików .txt w folderze z listenerami: " + folderPath);
            return;
        }

        for (File file : files) {
            loadListenersFromFile(file);
        }
        Log.imp("Załadowano listenery z " + files.length + " plików.");
    }

    private void loadListenersFromFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            ListenerType currentType = null;
            ListenerRule currentRule = null;

            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                if (trimmedLine.toLowerCase().startsWith("[") && trimmedLine.endsWith("listener]:")) {
                    String typeStr = trimmedLine.substring(1, trimmedLine.indexOf(" ")).toUpperCase();
                    try {
                        currentType = ListenerType.valueOf(typeStr);
                        ListenerRule globalRule = new ListenerRule(null);
                        listeners.get(currentType).add(globalRule);
                        currentRule = globalRule;
                    } catch (IllegalArgumentException e) {
                        currentType = null;
                        currentRule = null;
                    }
                    continue;
                }

                if (currentType != null) {
                    if (trimmedLine.toLowerCase().startsWith("[if ") && trimmedLine.endsWith("]:")) {
                        String condition = trimmedLine.substring(1, trimmedLine.length() - 2);
                        currentRule = new ListenerRule(condition);
                        listeners.get(currentType).add(currentRule);
                    } else if (currentRule != null && (line.startsWith("    ") || line.startsWith("\t"))) {
                        if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]")) {
                            String action = trimmedLine.substring(1, trimmedLine.length() - 1);
                            currentRule.addAction(action);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.error("Nie udało się wczytać listenera z pliku: " + file.getAbsolutePath(), e.getMessage());
        }
    }

    public void handleEvent(ListenerType type, Bot bot, String message) {
        List<ListenerRule> rules = listeners.get(type);
        if (rules == null || rules.isEmpty()) {
            return;
        }

        String code = extractCode(message);
        PlayerInfo playerInfo = extractPlayerInfo(message);

        for (ListenerRule rule : rules) {
            if (rule.isConditionMet(message, playerInfo)) {
                for (String actionTemplate : rule.getActions()) {

                    if (actionTemplate.contains("{code}") && (code == null || code.isEmpty())) {
                        continue;
                    }

                    String finalAction = actionTemplate.replace("{message}", message);
                    if (code != null) {
                        finalAction = finalAction.replace("{code}", code);
                    }
                    if (playerInfo.isPlayerMsg()) {
                        finalAction = finalAction.replace("{player}", playerInfo.getPlayerName());
                        finalAction = finalAction.replace("{playermsg}", playerInfo.getPlayerMessage());
                    }

                    String commandToExecute = "[" + finalAction + "]";
                    try {
                        bot.getScriptExecutor().submit(() -> {
                            try {
                                bot.processCommand(commandToExecute);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                Log.warn("Listener action was interrupted for bot " + bot.getNickname());
                            }
                        });
                    } catch (Exception e) {
                        Log.error("Błąd podczas wykonywania akcji listenera dla bota " + bot.getNickname(), e.getMessage());
                    }
                }
            }
        }
    }

    private PlayerInfo extractPlayerInfo(String message) {
        Matcher playerMatcher = PLAYER_CHAT_PATTERN.matcher(message);
        if (playerMatcher.matches()) {
            return new PlayerInfo(true, playerMatcher.group(1), playerMatcher.group(2));
        } else {
            return new PlayerInfo(false, null, null);
        }
    }

    private String extractCode(String message) {
        Matcher matcher = CODE_PATTERN.matcher(message);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group();
        }
        return lastMatch;
    }
}