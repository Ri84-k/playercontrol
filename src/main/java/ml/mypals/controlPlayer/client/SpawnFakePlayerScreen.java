package ml.mypals.controlPlayer.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Prompts for a safe Minecraft username before calling Carpet's /player <name> spawn. */
public final class SpawnFakePlayerScreen extends Screen {
    private final FakePlayerScreen parent;
    private EditBox nameField;
    private Button spawnButton;
    private String name = "";

    public SpawnFakePlayerScreen(FakePlayerScreen parent) {
        super(Component.translatable("controlplayer.menu.spawn.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int cy = height / 2;
        nameField = new EditBox(font, cx - 120, cy - 20, 240, 20,
                Component.translatable("controlplayer.menu.spawn.name"));
        nameField.setMaxLength(16);
        nameField.setHint(Component.translatable("controlplayer.menu.spawn.hint"));
        nameField.setValue(name);
        nameField.setResponder(value -> {
            name = value;
            if (spawnButton != null) {
                spawnButton.active = validName(value);
            }
        });
        addRenderableWidget(nameField);
        setInitialFocus(nameField);

        spawnButton = addRenderableWidget(Button.builder(
                Component.translatable("controlplayer.menu.spawn.confirm"),
                button -> spawn()).bounds(cx - 120, cy + 10, 116, 20).build());
        spawnButton.active = validName(name);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                button -> onClose()).bounds(cx + 4, cy + 10, 116, 20).build());
    }

    private static boolean validName(String candidate) {
        return candidate != null && candidate.matches("[A-Za-z0-9_]{1,16}");
    }

    private void spawn() {
        if (!validName(name) || minecraft == null || minecraft.player == null
                || minecraft.player.connection == null) {
            return;
        }
        minecraft.player.connection.sendCommand("player " + name + " spawn");
        parent.waitForSpawn(name);
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 60, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("controlplayer.menu.spawn.instructions"),
                width / 2, height / 2 - 43, 0xFFBBBBBB);
        if (!name.isEmpty() && !validName(name)) {
            graphics.centeredText(font, Component.translatable("controlplayer.menu.spawn.invalid"),
                    width / 2, height / 2 + 39, 0xFFFF7777);
        }
    }
}
