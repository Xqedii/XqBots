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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;


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

    private static boolean useProxies = false;
    private static final ArrayList<InetSocketAddress> proxies = new ArrayList<>();
    private static int proxyIndex = 0;
    private static int proxyCount = 0;
    private static ProxyInfo.Type proxyType;

    private static Timer timer = new Timer();

    public static void main(String[] args) throws Exception {

        Options options = new Options();

        options.addOption("c", "count", true, "bot count");

        Option addressOption = new Option("s", "server", true, "server IP[:port]");
        addressOption.setRequired(true);
        options.addOption(addressOption);

        Option delayOption = new Option("d", "delay", true, "connection delay (ms) <min> <max>");
        delayOption.setArgs(2);
        options.addOption(delayOption);

        options.addOption("l", "proxy-list", true, "Path or URL to proxy list file with proxy:port on every line");
        options.addOption("t", "proxy-type", true, "Proxy type: SOCKS4 or SOCKS5");

        options.addOption(null, "nicks", true, "Path to nicks file with nick on every line");

        options.addOption("g", "gravity", false, "Try to simulate gravity by falling down");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
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
                Log.error("Inavlid proxy type, use SOCKS4 or SOCKS5.");
                System.exit(1);
            }

            String proxyPath = cmd.getOptionValue("l");

            try {

                try {
                    URL url = new URL(proxyPath);

                    BufferedReader read = new BufferedReader(
                            new InputStreamReader(url.openStream()));

                    Log.info("Reading proxies from URL");
                    String line;
                    while ((line = read.readLine()) != null) {
                        try {
                            String[] parts = line.trim().split(":");
                            if (parts.length == 2) {
                                int port = Integer.parseInt(parts[1]);
                                proxies.add(new InetSocketAddress(parts[0], port));
                                proxyCount++;
                            }
                        }
                        catch (Exception ignored) { }
                    }
                    read.close();

                } catch (MalformedURLException e) {
                    Log.info("Specified proxy file is not a URL, trying to read file");

                    Scanner scanner = new Scanner(new File(proxyPath));
                    while (scanner.hasNextLine()) {
                        try {
                            String[] parts = scanner.nextLine().trim().split(":");
                            if (parts.length == 2) {
                                int port = Integer.parseInt(parts[1]);
                                proxies.add(new InetSocketAddress(parts[0], port));
                                proxyCount++;
                            }
                        }
                        catch (Exception ignored) { }
                    }
                    scanner.close();
                }
            } catch (FileNotFoundException e) {
                Log.error("Invalid proxy list file path.");
                System.exit(1);
            }

            if (proxyCount > 0) {
                useProxies = true;
                Log.info("Loaded " + proxyCount + " valid proxies");
            } else {
                Log.error("No valid proxies loaded");
                System.exit(1);
            }

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
            Record[] records = new Lookup("_minecraft._tcp." + address, Type.SRV).run();
            if (records != null) {
                for (Record record : records) {
                    SRVRecord srv = (SRVRecord) record;
                    address = srv.getTarget().toString().replaceFirst("\\.$", "");
                    port = srv.getPort();
                }
            }
        }

        NickGenerator nickGen = new NickGenerator();

        if (cmd.hasOption("nicks")) {
            Log.info("Loading nicknames from specified file");
            int nicksCount = nickGen.loadFromFile(cmd.getOptionValue("nicks"));

            if (nicksCount == 0) {
                Log.error("No valid nicknames loaded");
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
        ServerStatusInfo statusInfo = serverInfo.getStatusInfo();
        Log.info(
                "Server version: "
                        + statusInfo.getVersionInfo().getVersionName()
                        + " (" + statusInfo.getVersionInfo().getProtocolVersion()
                        + ")"
        );
        Log.info("Player Count: " + statusInfo.getPlayerInfo().getOnlinePlayers()
                + " / " + statusInfo.getPlayerInfo().getMaxPlayers());
        Log.info();

        new Thread(() -> {
            for (int i = 0; i < botCount; i++) {
                try {
                    ProxyInfo proxyInfo = null;
                    if (useProxies) {
                        InetSocketAddress proxySocket = proxies.get(proxyIndex);

                        if (!minimal) {
                            Log.info(
                                    "Using proxy: (" + proxyIndex + ")",
                                    proxySocket.getHostString() + ":" + proxySocket.getPort()
                            );
                        }

                        proxyInfo = new ProxyInfo(
                                proxyType,
                                proxySocket
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
                            proxyInfo
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
        String[] parts = commandLine.trim().split(" ");
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
                Log.info("!bot [id] [command] - Execute command for bot");
                Log.info("[text] - Send chat message/command");
                Log.info("");
                break;

            case "crash":
                Log.info("Executing 'startSendingPackets' for all " + bots.size() + " bots...");
                bots.forEach(Bot::startSendingPackets);
                break;

            case "dropall":
                Log.info("Executing 'dropHotbarItems' for all " + bots.size() + " bots...");
                bots.forEach(Bot::dropHotbarItems);
                break;

            case "ch":
            case "channel":
            case "sector":
                if (parts.length < 2) {
                    Log.warn("Usage: !channel [number]");
                    break;
                }
                int sector;
                try {
                    sector = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    Log.warn("Invalid sector number: " + parts[1]);
                    break;
                }
                Log.info("Connecting all bots to sector " + "#" + sector);
                bots.forEach(bot -> bot.changeSector(sector, "sector", 1));
                break;

            case "headroll":
                if (parts.length < 2) {
                    Log.warn("Usage: !headroll on/off");
                    break;
                }
                String option = parts[1].toLowerCase();
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

            case "list":
                Log.info("Listing all connected bots (" + bots.size() + " total):");
                for (int i = 0; i < bots.size(); i++) {
                    Bot bot = bots.get(i);
                    Log.info("  [" + (i + 1) + "] " + bot.getNickname());
                }
                break;

            case "bot":
                if (parts.length < 3) {
                    Log.warn("Usage: !bot <ID> <command>");
                    Log.warn("Available sub-commands: start, drop, headroll on/off");
                    return;
                }

                int botId;
                try {
                    botId = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    Log.error("Invalid bot ID: '" + parts[1] + "'. Must be a number.");
                    return;
                }

                int botIndex = botId - 1;

                if (botIndex < 0 || botIndex >= bots.size()) {
                    Log.error("Bot ID out of bounds. Valid IDs are from 1 to " + bots.size());
                    return;
                }

                Bot targetBot = bots.get(botIndex);
                String subCommand = parts[2].toLowerCase();

                Log.info("Executing '" + subCommand + "' for bot " + botId + " (" + targetBot.getNickname() + ")");

                switch (subCommand) {
                    case "crash":
                        targetBot.startSendingPackets();
                        break;
                    case "drop":
                        targetBot.dropHotbarItems();
                        break;
                    case "headroll":
                        if (parts.length < 4) {
                            Log.warn("Usage: !bot <ID> headroll on/off");
                            break;
                        }
                        String headrollOption = parts[3].toLowerCase();
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

            default:
                Log.warn("Unknown command: '" + command + "'");
                break;
        }
    }


    public static synchronized void renewMainListener() {
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
        bot = null;
    }

    public static long getRandomDelay() {
        return random.nextInt(delayMax - delayMin) + delayMin;
    }

    public static boolean isMinimal() {
        return minimal;
    }
}