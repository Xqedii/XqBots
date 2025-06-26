package dev.xqedii;

public class PlayerInfo {
    private final boolean isPlayerMsg;
    private final String playerName;
    private final String playerMessage;

    public PlayerInfo(boolean isPlayerMsg, String playerName, String playerMessage) {
        this.isPlayerMsg = isPlayerMsg;
        this.playerName = playerName;
        this.playerMessage = playerMessage;
    }

    public boolean isPlayerMsg() {
        return isPlayerMsg;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerMessage() {
        return playerMessage;
    }
}