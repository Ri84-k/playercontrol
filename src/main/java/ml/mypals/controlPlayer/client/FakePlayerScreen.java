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
    private static final int ROWS_PER_PAGE = 7;

    private List<String> playerNames = List.of();
    private boolean swapped;
    private boolean canSwap;
    private boolean loaded;
    private int page;

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
    }

    @Override
    protected void init() {
        super.init();
        int center = width / 2;
        int rowTop = height / 2 - 83;
        int totalPages = Math.max(1, (playerNames.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);

        for (int i = page * ROWS_PER_PAGE; i < Math.min(playerNames.size(), (page + 1) * ROWS_PER_PAGE); i++) {
            String name = playerNames.get(i);
            int y = rowTop + (i % ROWS_PER_PAGE) * 22;
            addRenderableWidget(Button.builder(Component.literal(name),
                    b -> {}).bounds(center - 155, y, 205, 20).build()).active = false;
            Button action = Button.builder(Component.translatable("controlplayer.menu.swap"),
                    b -> runCommand("controlPlayer swap " + name))
                    .bounds(center + 54, y, 100, 20).build();
            action.active = canSwap && !swapped && !name.equalsIgnoreCase(Minecraft.getInstance().player.getGameProfile().name());
            addRenderableWidget(action);
        }

        int footer = rowTop + ROWS_PER_PAGE * 22 + 8;
        if (totalPages > 1) {
            Button previous = Button.builder(Component.literal("<"), b -> {
                page--;
                rebuildWidgets();
            }).bounds(center - 151, footer, 42, 20).build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.literal(">"), b -> {
                page++;
                rebuildWidgets();
            }).bounds(center - 104, footer, 42, 20).build();
            next.active = page + 1 < totalPages;
            addRenderableWidget(next);
        }

        Button release = Button.builder(Component.translatable("controlplayer.menu.release"),
                b -> runCommand("controlPlayer release"))
                .bounds(center - 54, footer, 105, 20).build();
        release.active = swapped;
        addRenderableWidget(release);

        addRenderableWidget(Button.builder(Component.translatable("controlplayer.menu.refresh"),
                b -> requestList()).bounds(center + 55, footer, 99, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> onClose()).bounds(center - 76, footer + 25, 152, 20).build());

        if (!loaded) requestList();
    }

    private void requestList() {
        if (ClientPlayNetworking.canSend(FakePlayerMenuPackets.Request.TYPE)) {
            ClientPlayNetworking.send(FakePlayerMenuPackets.Request.INSTANCE);
        }
    }

    private void runCommand(String command) {
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
                    width / 2, height / 2 + 125, 0xFFFFCC55);
        }
    }
}
