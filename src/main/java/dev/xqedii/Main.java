package dev.xqedii;

import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.github.steveice10.packetlib.ProxyInfo;
import org.apache.commons.cli.*;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Type;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import java.util.Arrays;


public class Main {

    public static boolean coloredChat = true;
    static ArrayList<Bot> bots = new ArrayList<>();
    private static int triedToConnect;
    private static int botCount;
    private static boolean isMainListenerMissing = true;
    private static final SecureRandom random = new SecureRandom();
    private static int delayMin = 4000;
    private static int delayMax = 5000;
    private static boolean minimal = false;
    private static boolean mostMinimal = false;
    private static volatile boolean isAutoSwapRunning = false;

    private static boolean useProxies = false;
    private static final ArrayList<ProxyDetails> proxies = new ArrayList<>();
    private static int proxyIndex = 0;
    private static int proxyCount = 0;
    private static ProxyInfo.Type proxyType;

    private static Timer timer = new Timer();

    private static volatile boolean isAsciiSequenceRunning = false;
    private static final List<String> DEFAULT_ASCII_MESSAGES = Arrays.asList(
            "a", "s", "c", "i", "i"
    );

    private static class ProxyDetails {
        final String host;
        final int port;
        final String username;
        final String password;

        public ProxyDetails(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }
    }


    public static void main(String[] args) throws Exception {

        Options options = new Options();

        options.addOption("c", "count", true, "bot count");

        Option addressOption = new Option("s", "server", true, "server IP[:port]");
        addressOption.setRequired(true);
        options.addOption(addressOption);

        Option delayOption = new Option("d", "delay", true, "connection delay (ms) <min> <max>");
        delayOption.setArgs(2);
        options.addOption(delayOption);

        options.addOption("l", "proxy-list", true, "Path or URL to proxy list file with user:pass@host:port on every line");
        options.addOption("t", "proxy-type", true, "Proxy type: SOCKS4 or SOCKS5");

        options.addOption(null, "nicks", true, "Path to nicks file with nick on every line");
        options.addOption("a", "actions", true, "Path to actions script file");
        options.addOption("g", "gravity", false, "Try to simulate gravity by falling down");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("bot-utility", options);
            System.exit(1);
        }

        if (cmd.hasOption("g")) {
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    bots.forEach(Bot::fallDown);
                }
            }, 1000L, 500L);
        }

        if (cmd.hasOption('t') && cmd.hasOption('l')) {
            String typeStr = cmd.getOptionValue('t').toUpperCase();

            try {
                proxyType = ProxyInfo.Type.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                Log.error("Invalid proxy type, use SOCKS4 or SOCKS5.");
                System.exit(1);
            }

            String proxyPath = cmd.getOptionValue("l");

            try {
                ArrayList<String> lines = new ArrayList<>();
                try {
                    URL url = new URL(proxyPath);
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                        Log.info("Reading proxies from URL...");
                        String line;
                        while ((line = reader.readLine()) != null) {
                            lines.add(line);
                        }
                    }
                } catch (MalformedURLException e) {
                    Log.info("Specified proxy source is not a URL, trying to read as a file...");
                    try (Scanner scanner = new Scanner(new File(proxyPath))) {
                        while (scanner.hasNextLine()) {
                            lines.add(scanner.nextLine());
                        }
                    }
                }

                for (String line : lines) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;

                    try {
                        String[] authAndHost = line.trim().split("@");
                        if (authAndHost.length != 2) continue;

                        String[] userAndPass = authAndHost[0].split(":", 2);
                        if (userAndPass.length != 2) continue;

                        String[] hostAndPort = authAndHost[1].split(":", 2);
                        if (hostAndPort.length != 2) continue;

                        String username = userAndPass[0];
                        String password = userAndPass[1];
                        String host = hostAndPort[0];
                        int port = Integer.parseInt(hostAndPort[1]);

                        proxies.add(new ProxyDetails(host, port, username, password));
                        proxyCount++;

                    } catch (Exception ex) {
                    }
                }

            } catch (IOException e) {
                Log.error("Could not read proxy list from: " + proxyPath);
                System.exit(1);
            }

            if (proxyCount > 0) {
                useProxies = true;
                Log.info("Loaded " + proxyCount + " valid proxies.");
            } else {
                Log.error("No valid proxies loaded from the list.");
                System.exit(1);
            }
        }

        String actionsFilePath = cmd.getOptionValue("a");
        if (actionsFilePath != null) {
            Log.info("Using actions script from: " + actionsFilePath);
        }

        botCount = Integer.parseInt(cmd.getOptionValue('c', "1"));

        if (cmd.hasOption('d')) {
            String[] delays = cmd.getOptionValues('d');
            delayMin = Integer.parseInt(delays[0]);
            delayMax = delayMin + 1;
            if (delays.length == 2) {
                delayMax = Integer.parseInt(delays[1]);
            }
            if (delayMax <= delayMin) {
                throw new IllegalArgumentException("delay max must not be equal or lower than delay min");
            }
        }

        String address = cmd.getOptionValue('s');

        int port = 25565;
        if (address.contains(":")) {
            String[] split = address.split(":", 2);
            address = split[0];
            port = Integer.parseInt(split[1]);
        } else {
            try {
                Record[] records = new Lookup("_minecraft._tcp." + address, Type.SRV).run();
                if (records != null && records.length > 0) {
                    SRVRecord srv = (SRVRecord) records[0];
                    address = srv.getTarget().toString().replaceFirst("\\.$", "");
                    port = srv.getPort();
                }
            } catch (Exception e) {
                Log.warn("SRV record lookup failed for " + address);
            }
        }

        NickGenerator nickGen = new NickGenerator();

        if (cmd.hasOption("nicks")) {
            Log.info("Loading nicknames from specified file...");
            int nicksCount = nickGen.loadFromFile(cmd.getOptionValue("nicks"));

            if (nicksCount == 0) {
                Log.error("No valid nicknames loaded.");
                System.exit(1);
            }

            if (nicksCount < botCount) {
                Log.warn("Nickname count is lower than bot count!");
                Thread.sleep(3000);
            }
        }

        InetSocketAddress inetAddr = new InetSocketAddress(address, port);

        Log.info("IP:", inetAddr.getHostString());
        Log.info("Port: " + inetAddr.getPort());
        Log.info("Bot count: " + botCount);

        ServerInfo serverInfo = new ServerInfo(inetAddr);
        serverInfo.requestInfo();
        if (serverInfo.getStatusInfo() != null) {
            ServerStatusInfo statusInfo = serverInfo.getStatusInfo();
            Log.info(
                    "Server version: "
                            + statusInfo.getVersionInfo().getVersionName()
                            + " (" + statusInfo.getVersionInfo().getProtocolVersion()
                            + ")"
            );
            Log.info("Player Count: " + statusInfo.getPlayerInfo().getOnlinePlayers()
                    + " / " + statusInfo.getPlayerInfo().getMaxPlayers());
        }
        Log.info();

        new Thread(() -> {
            for (int i = 0; i < botCount; i++) {
                try {
                    ProxyInfo proxyInfo = null;
                    if (useProxies) {
                        ProxyDetails details = proxies.get(proxyIndex);

                        if (!minimal) {
                            Log.info(
                                    "Using proxy: (" + (proxyIndex + 1) + "/" + proxyCount + ")",
                                    details.host + ":" + details.port
                            );
                        }

                        proxyInfo = new ProxyInfo(
                                proxyType,
                                new InetSocketAddress(details.host, details.port),
                                details.username,
                                details.password
                        );

                        if (proxyIndex < (proxyCount - 1)) {
                            proxyIndex++;
                        } else {
                            proxyIndex = 0;
                        }
                    }

                    Bot bot = new Bot(
                            i + 1,
                            nickGen.nextNick(),
                            inetAddr,
                            proxyInfo,
                            actionsFilePath
                    );
                    bot.start();

                    if (!mostMinimal) bots.add(bot);

                    triedToConnect++;

                    if (isMainListenerMissing && !isMinimal()) {
                        isMainListenerMissing = false;
                        bot.registerMainListener();
                    }

                    if (i < botCount - 1) {
                        long delay = getRandomDelay();
                        Log.info("Waiting", delay + "", "ms");
                        Thread.sleep(delay);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();

        Scanner scanner = new Scanner(System.in);

        Log.info("Console ready. Type a message to send it with all bots, or use !<command>.");

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isEmpty()) continue;

            if (line.startsWith("!")) {
                handleCommand(line.substring(1));
            } else {
                Log.info("Sending chat message from all bots: " + line);
                bots.forEach(bot -> bot.sendChat(line));
            }

            Thread.sleep(50);
        }
    }

    private static void handleCommand(String commandLine) {
        String[] parts = commandLine.trim().split(" ", 2);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "help":
                Log.info("");
                Log.info("!help - Displays this menu");
                Log.info("!crash - Start server crashing");
                Log.info("!dropall - Drop all items");
                Log.info("!headroll on/off - Start headroll");
                Log.info("!channel [number] - Change channel");
                Log.info("!list - List of all connected bots");
                Log.info("!ascii [file_path] - Send a sequence of messages from a file (or default)");
                Log.info("!bot [id] [command] - Execute command for bot");
                Log.info("[text] - Send chat message/command");
                Log.info("");
                break;
            case "swap":
                if (isAutoSwapRunning) {
                    Log.info("Stopping automatic sector swapping for all bots...");
                    bots.forEach(Bot::stopSectorSwapping);
                    isAutoSwapRunning = false;
                } else {
                    Log.info("Starting automatic sector swapping for all bots (1-5, 20s interval)...");
                    bots.forEach(Bot::startSectorSwapping);
                    isAutoSwapRunning = true;
                }
                break;
            case "ascii":
                List<String> messagesToUse;
                if (parts.length > 1) {
                    String filePath = parts[1];
                    messagesToUse = loadMessagesFromFile(filePath);
                } else {
                    Log.info("No file path provided, using default ASCII messages.");
                    messagesToUse = DEFAULT_ASCII_MESSAGES;
                }

                if (messagesToUse != null && !messagesToUse.isEmpty()) {
                    triggerAsciiSequence(messagesToUse);
                }
                break;

            case "crash": {
                Log.info("Executing 'startSendingPackets' for all " + bots.size() + " bots...");
                bots.forEach(Bot::startSendingPackets);
                break;
            }
            case "dropall": {
                Log.info("Executing 'dropHotbarItems' for all " + bots.size() + " bots...");
                bots.forEach(Bot::dropHotbarItems);
                break;
            }
            case "ch":
            case "channel":
            case "sector": {
                String[] sectorParts = commandLine.trim().split(" ");
                if (sectorParts.length < 2) {
                    Log.warn("Usage: !sector <number | auto<N> | distribute <N>>");
                    Log.warn("Examples: !sector 5 | !sector auto4 | !sector distribute 8");
                    break;
                }

                String commandType = sectorParts[1].toLowerCase();

                if (commandType.equals("distribute")) {
                    if (sectorParts.length < 3) {
                        Log.warn("Usage: !sector distribute <number_of_sectors>");
                        break;
                    }
                    try {
                        int totalSectors = Integer.parseInt(sectorParts[2]);
                        if (totalSectors <= 0) {
                            Log.error("Number of sectors must be a positive number.");
                            break;
                        }

                        int botCount = bots.size();
                        if (botCount == 0) {
                            Log.warn("No bots connected to distribute.");
                            break;
                        }

                        Log.info("Distributing " + botCount + " bots evenly across " + totalSectors + " sectors...");

                        for (int i = 0; i < botCount; i++) {
                            Bot currentBot = bots.get(i);
                            int targetSector = (i % totalSectors) + 1;

                            Log.info("Bot #" + currentBot.getBotId() + " (" + currentBot.getNickname() + ") -> Sector " + targetSector);
                            currentBot.changeSector(targetSector, "sector", 0);
                        }

                    } catch (NumberFormatException e) {
                        Log.error("Invalid number of sectors: '" + sectorParts[2] + "'");
                    }

                } else if (commandType.startsWith("auto")) {
                    try {
                        String numberPart = commandType.substring(4);
                        int groupSize = Integer.parseInt(numberPart);

                        if (groupSize <= 0) {
                            Log.error("Group size for auto-sector must be a positive number.");
                            break;
                        }

                        Log.info("Distributing bots into sectors in groups of " + groupSize + "...");

                        for (Bot bot : bots) {
                            int targetSector = ((bot.getBotId() - 1) / groupSize) + 1;
                            Log.info("Bot #" + bot.getBotId() + " (" + bot.getNickname() + ") -> Sector " + targetSector);
                            bot.changeSector(targetSector, "sector", 0);
                        }
                    } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                        Log.error("Invalid auto-sector format. Use 'auto<number>', e.g., '!sector auto3'.");
                    }

                } else {
                    try {
                        int sectorNumber = Integer.parseInt(commandType);
                        Log.info("Connecting all bots to sector #" + sectorNumber);
                        bots.forEach(bot -> bot.changeSector(sectorNumber, "sector", 0));
                    } catch (NumberFormatException e) {
                        Log.warn("Invalid command. Usage: !sector <number|auto<N>|distribute <N>>");
                    }
                }
                break;
            }
            case "headroll": {
                String[] headrollParts = commandLine.trim().split(" ");
                if (headrollParts.length < 2) {
                    Log.warn("Usage: !headroll on/off");
                    break;
                }
                String option = headrollParts[1].toLowerCase();
                switch (option) {
                    case "on":
                        Log.info("Enabling headroll for all bots...");
                        bots.forEach(Bot::startHeadroll);
                        break;
                    case "off":
                        Log.info("Disabling headroll for all bots...");
                        bots.forEach(Bot::stopHeadroll);
                        break;
                    default:
                        Log.warn("Unknown headroll option: " + option);
                }
                break;
            }
            case "list": {
                Log.info("Listing all connected bots (" + bots.size() + " total):");
                for (int i = 0; i < bots.size(); i++) {
                    Bot bot = bots.get(i);
                    Log.info("  [" + (i + 1) + "] " + bot.getNickname());
                }
                break;
            }
            case "bot": {
                String[] botParts = commandLine.trim().split(" ");
                if (botParts.length < 3) {
                    Log.warn("Usage: !bot <ID> <command>");
                    Log.warn("Available sub-commands: start, drop, headroll on/off");
                    return;
                }

                int botId;
                try {
                    botId = Integer.parseInt(botParts[1]);
                } catch (NumberFormatException e) {
                    Log.error("Invalid bot ID: '" + botParts[1] + "'. Must be a number.");
                    return;
                }

                int botIndex = botId - 1;

                if (botIndex < 0 || botIndex >= bots.size()) {
                    Log.error("Bot ID out of bounds. Valid IDs are from 1 to " + bots.size());
                    return;
                }

                Bot targetBot = bots.get(botIndex);
                String subCommand = botParts[2].toLowerCase();

                Log.info("Executing '" + subCommand + "' for bot " + botId + " (" + targetBot.getNickname() + ")");

                switch (subCommand) {
                    case "crash":
                        targetBot.startSendingPackets();
                        break;
                    case "drop":
                        targetBot.dropHotbarItems();
                        break;
                    case "headroll":
                        if (botParts.length < 4) {
                            Log.warn("Usage: !bot <ID> headroll on/off");
                            break;
                        }
                        String headrollOption = botParts[3].toLowerCase();
                        switch (headrollOption) {
                            case "on":
                                targetBot.startHeadroll();
                                break;
                            case "off":
                                targetBot.stopHeadroll();
                                break;
                            default:
                                Log.warn("Unknown headroll option: '" + headrollOption + "'");
                                break;
                        }
                        break;
                    default:
                        Log.warn("Unknown sub-command for !bot: '" + subCommand + "'");
                        break;
                }
                break;
            }
            default:
                Log.warn("Unknown command: '" + command + "'");
                break;
        }
    }

    /**
     * Wczytuje wiadomości z podanego pliku, jedna wiadomość na linię.
     */
    public static synchronized void setAutoSwapState(boolean isRunning) {
        isAutoSwapRunning = isRunning;
    }
    public static List<String> loadMessagesFromFile(String filePath) {
        try {
            Log.info("Loading ASCII messages from file: " + filePath);
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            if (lines.isEmpty()) {
                Log.error("ASCII file is empty: " + filePath);
                return null;
            }
            return lines;
        } catch (IOException e) {
            Log.error("Could not read ASCII file: " + filePath + ". Error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Uruchamia sekwencję wiadomości ASCII, używając podanej listy wiadomości.
     */
    public static synchronized void triggerAsciiSequence(List<String> messages) {
        if (isAsciiSequenceRunning) {
            Log.info("ASCII sequence is already running. Ignoring request.");
            return;
        }
        if (messages == null || messages.isEmpty()) {
            Log.error("Cannot start ASCII sequence with no messages.");
            return;
        }
        isAsciiSequenceRunning = true;

        new Thread(() -> {
            Log.info("Starting ASCII sequence...");
            try {
                ArrayList<Bot> botsSnapshot = new ArrayList<>(bots);
                int groupSize = messages.size();

                for (int i = 0; i < botsSnapshot.size(); i++) {
                    Bot currentBot = botsSnapshot.get(i);
                    String message = messages.get(i % groupSize);
                    currentBot.sendChat(message);
                    Thread.sleep(20);
                }
                Log.info("ASCII sequence finished.");
            } catch (InterruptedException e) {
                Log.warn("ASCII sequence was interrupted.");
                Thread.currentThread().interrupt();
            } finally {
                isAsciiSequenceRunning = false;
            }
        }).start();
    }


    public static synchronized void renewMainListener() {
        if (bots.isEmpty()) return;
        bots.get(0).registerMainListener();
    }

    public static synchronized void removeBot(Bot bot) {
        bots.remove(bot);
        if (bot.hasMainListener()) {
            Log.info("Bot with MainListener removed");
            isMainListenerMissing = true;
        }
        if (bots.size() > 0) {
            if (isMainListenerMissing && !isMinimal()) {
                Log.info("Renewing MainListener");
                renewMainListener();
                isMainListenerMissing = false;
            }
        } else {
            if (triedToConnect == botCount) {
                Log.error("All bots disconnected, exiting");
                System.exit(0);
            }
        }
    }

    public static long getRandomDelay() {
        return random.nextInt(delayMax - delayMin) + delayMin;
    }

    public static boolean isMinimal() {
        return minimal;
    }
}