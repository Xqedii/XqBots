package dev.xqedii;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListenerRule {

    private final String condition;
    private final List<String> actions;

    private static final Pattern CONDITION_PATTERN = Pattern.compile("if\\s+(\\{\\w+})\\s+(contains|is|notcontains|isnot)\\s+\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    /**
     * Konstruktor reguły.
     * @param condition Warunek w formacie 'if {placeholder} operator "wartość"', lub null dla reguły globalnej.
     */
    public ListenerRule(String condition) {
        this.condition = condition;
        this.actions = new ArrayList<>();
    }

    public void addAction(String action) {
        this.actions.add(action);
    }

    public List<String> getActions() {
        return actions;
    }

    public boolean isConditionMet(String message, PlayerInfo playerInfo) {
        if (condition == null) {
            return !playerInfo.isPlayerMsg();
        }

        Matcher matcher = CONDITION_PATTERN.matcher(condition);
        if (!matcher.matches()) {
            Log.warn("Nieprawidłowy format warunku IF: " + condition);
            return false;
        }

        String placeholder = matcher.group(1).toLowerCase();
        String operator = matcher.group(2).toLowerCase();
        String value = matcher.group(3);

        String textToCompare;
        switch (placeholder) {
            case "{message}":
                textToCompare = message;
                break;
            case "{player}":
                if (!playerInfo.isPlayerMsg()) return false;
                textToCompare = playerInfo.getPlayerName();
                break;
            case "{playermsg}":
                if (!playerInfo.isPlayerMsg()) return false;
                textToCompare = playerInfo.getPlayerMessage();
                break;
            default:
                Log.warn("Nieznany placeholder w warunku IF: " + placeholder);
                return false;
        }

        if (textToCompare == null) {
            return false;
        }

        switch (operator) {
            case "is":
                return textToCompare.equalsIgnoreCase(value);
            case "isnot":
                return !textToCompare.equalsIgnoreCase(value);
            case "contains":
                return textToCompare.toLowerCase().contains(value.toLowerCase());
            case "notcontains":
                return !textToCompare.toLowerCase().contains(value.toLowerCase());
            default:
                return false;
        }
    }
}