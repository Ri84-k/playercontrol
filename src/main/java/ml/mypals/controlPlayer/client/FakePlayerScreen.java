package ml.mypals.controlPlayer.client;

import ml.mypals.controlPlayer.net.FakePlayerMenuPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A client-only, keybind-opened manager showing current Carpet fake players. */
public final class FakePlayerScreen extends Screen {
    private static final int ROWS_PER_PAGE = 5;

    private List<String> playerNames = List.of();
    private boolean swapped;
    private boolean canSwap;
    private boolean loaded;
    private int page;
    private String awaitingSpawn;
    private int spawnRefreshTicks;

    public FakePlayerScreen() {
        super(Component.translatable("controlplayer.menu.title"));
    }

    public FakePlayerScreen(FakePlayerMenuPackets.ListResponse response) {
        this();
        updateData(response);
    }

    public void setPlayers(FakePlayerMenuPackets.ListResponse response) {
        updateData(response);
        rebuildWidgets();
    }

    private void updateData(FakePlayerMenuPackets.ListResponse response) {
        this.playerNames = response.playerNames();
        this.swapped = response.swapped();
        this.canSwap = response.maySwap();
        this.loaded = true;
        this.page = Math.min(this.page, Math.max(0, (playerNames.size() - 1) / ROWS_PER_PAGE));
        if (awaitingSpawn != null && playerNames.stream().anyMatch(n -> n.equalsIgnoreCase(awaitingSpawn))) {
            // Place the newly spawned player on the visible page.
            for (int i = 0; i < playerNames.size(); i++) {
                if (playerNames.get(i).equalsIgnoreCase(awaitingSpawn)) {
                    page = i / ROWS_PER_PAGE;
                    break;
                }
            }
            awaitingSpawn = null;
        }
    }

    /** Carpet may spawn fake players asynchronously, so briefly poll after a spawn command. */
    public void waitForSpawn(String name) {
        awaitingSpawn = name;
        spawnRefreshTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (awaitingSpawn != null && spawnRefreshTicks++ < 200
                && spawnRefreshTicks % 20 == 0) {
            requestList();
        }
        if (spawnRefreshTicks >= 200) {
            awaitingSpawn = null;
        }
    }

    @Override
    protected void init() {
        super.init();
        int center = width / 2;
        int rowTop = height / 2 - 74;
        int totalPages = Math.max(1, (playerNames.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);

        for (int i = page * ROWS_PER_PAGE; i < Math.min(playerNames.size(), (page + 1) * ROWS_PER_PAGE); i++) {
            String name = playerNames.get(i);
            int y = rowTop + (i % ROWS_PER_PAGE) * 22;
            Button playerLabel = Button.builder(Component.literal(name), b -> {})
                    .bounds(center - 168, y, 177, 20).build();
            playerLabel.active = false;
            addRenderableWidget(playerLabel);

            Button swapButton = Button.builder(Component.translatable("controlplayer.menu.swap"),
                    b -> runAndClose("controlPlayer swap " + name))
                    .bounds(center + 13, y, 70, 20).build();
            swapButton.active = canSwap && !swapped
                    && minecraft != null && minecraft.player != null
                    && !name.equalsIgnoreCase(minecraft.player.getGameProfile().name());
            addRenderableWidget(swapButton);

            Button actionsButton = Button.builder(Component.translatable("controlplayer.menu.actions"),
                    b -> minecraft.gui.setScreen(new FakePlayerActionsScreen(this, name, canSwap)))
                    .bounds(center + 87, y, 81, 20).build();
            actionsButton.active = canSwap;
            addRenderableWidget(actionsButton);
        }

        int footer = rowTop + ROWS_PER_PAGE * 22 + 8;
        addRenderableWidget(Button.builder(Component.translatable("controlplayer.menu.status"),
                b -> runAndClose("controlPlayer status"))
                .bounds(center - 168, footer, 79, 20).build());

        Button spawn = Button.builder(Component.translatable("controlplayer.menu.spawn"),
                b -> minecraft.gui.setScreen(new SpawnFakePlayerScreen(this)))
                .bounds(center - 85, footer, 79, 20).build();
        spawn.active = canSwap;
        addRenderableWidget(spawn);

        Button release = Button.builder(Component.translatable("controlplayer.menu.release"),
                b -> runAndClose("controlPlayer release"))
                .bounds(center - 2, footer, 79, 20).build();
        release.active = swapped;
        addRenderableWidget(release);

        addRenderableWidget(Button.builder(Component.translatable("controlplayer.menu.refresh"),
                b -> requestList()).bounds(center + 81, footer, 87, 20).build());

        if (totalPages > 1) {
            Button previous = Button.builder(Component.literal("<"),
                    b -> {
                        page--;
                        rebuildWidgets();
                    }).bounds(center - 97, footer + 26, 42, 20).build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.literal(">"),
                    b -> {
                        page++;
                        rebuildWidgets();
                    }).bounds(center + 55, footer + 26, 42, 20).build();
            next.active = page + 1 < totalPages;
            addRenderableWidget(next);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> onClose()).bounds(center - 52, footer + 26, 104, 20).build());

        if (!loaded) requestList();
    }

    public void requestList() {
        if (ClientPlayNetworking.canSend(FakePlayerMenuPackets.Request.TYPE)) {
            ClientPlayNetworking.send(FakePlayerMenuPackets.Request.INSTANCE);
        }
    }

    public void runAndReturn(String command) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand(command);
            minecraft.gui.setScreen(this);
            requestList();
        }
    }

    private void runAndClose(String command) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand(command);
            minecraft.gui.setScreen(null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 109, 0xFFFFFFFF);
        Component info = !loaded
                ? Component.translatable("controlplayer.menu.loading")
                : playerNames.isEmpty()
                ? Component.translatable("controlplayer.menu.empty")
                : Component.translatable("controlplayer.menu.count", playerNames.size());
        graphics.centeredText(font, info, width / 2, height / 2 - 96, 0xFFAAAAAA);
        if (swapped) {
            graphics.centeredText(font, Component.translatable("controlplayer.menu.swapped"),
                    width / 2, height / 2 + 103, 0xFFFFCC55);
        }
    }
}
