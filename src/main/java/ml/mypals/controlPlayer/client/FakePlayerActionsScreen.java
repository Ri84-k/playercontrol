package ml.mypals.controlPlayer.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Carpet controls for the selected online fake player. */
public final class FakePlayerActionsScreen extends Screen {
    private final FakePlayerScreen parent;
    private final String name;
    private final boolean permitted;

    public FakePlayerActionsScreen(FakePlayerScreen parent, String name, boolean permitted) {
        super(Component.translatable("controlplayer.menu.actions.title", name));
        this.parent = parent;
        this.name = name;
        this.permitted = permitted;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int y = height / 2 - 75;
        addAction(cx - 144, y, "controlplayer.menu.actions.stop", "stop");
        addAction(cx + 4, y, "controlplayer.menu.actions.attack", "attack once");
        addAction(cx - 144, y + 24, "controlplayer.menu.actions.attack_continuous", "attack continuous");
        addAction(cx + 4, y + 24, "controlplayer.menu.actions.use", "use once");
        addAction(cx - 144, y + 48, "controlplayer.menu.actions.use_continuous", "use continuous");
        addAction(cx + 4, y + 48, "controlplayer.menu.actions.jump", "jump once");
        addAction(cx - 144, y + 72, "controlplayer.menu.actions.move", "move forward");
        addAction(cx + 4, y + 72, "controlplayer.menu.actions.sneak", "sneak");

        Button kill = Button.builder(Component.translatable("controlplayer.menu.actions.kill"),
                button -> minecraft.gui.setScreen(new ConfirmFakePlayerKillScreen(this, name)))
                .bounds(cx - 144, y + 100, 140, 20).build();
        kill.active = permitted;
        addRenderableWidget(kill);

        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                button -> onClose()).bounds(cx + 4, y + 100, 140, 20).build());
    }

    private void addAction(int x, int y, String label, String suffix) {
        Button button = Button.builder(Component.translatable(label),
                b -> perform(suffix)).bounds(x, y, 140, 20).build();
        button.active = permitted;
        addRenderableWidget(button);
    }

    private void perform(String action) {
        // Names come from the server's list; validate again before issuing a command.
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return;
        parent.sendAction(action, name, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 109, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("controlplayer.menu.actions.online"),
                width / 2, height / 2 - 94, 0xFF99FF99);
        graphics.centeredText(font, Component.translatable("controlplayer.menu.actions.description"),
                width / 2, height / 2 + 51, 0xFFBBBBBB);
    }

    private static final class ConfirmFakePlayerKillScreen extends Screen {
        private final FakePlayerActionsScreen parent;
        private final String name;

        private ConfirmFakePlayerKillScreen(FakePlayerActionsScreen parent, String name) {
            super(Component.translatable("controlplayer.menu.actions.kill"));
            this.parent = parent;
            this.name = name;
        }

        @Override
        protected void init() {
            super.init();
            int cx = width / 2;
            int cy = height / 2;
            addRenderableWidget(Button.builder(Component.translatable("controlplayer.menu.actions.confirm_kill"),
                    button -> {
                        if (name.matches("[A-Za-z0-9_]{1,16}")) {
                            parent.parent.sendAction("kill", name, false);
                        }
                    }).bounds(cx - 120, cy + 10, 116, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                    button -> onClose()).bounds(cx + 4, cy + 10, 116, 20).build());
        }

        @Override
        public void onClose() {
            if (minecraft != null) minecraft.gui.setScreen(parent);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            graphics.centeredText(font, Component.translatable("controlplayer.menu.actions.kill_warning", name),
                    width / 2, height / 2 - 24, 0xFFFF7777);
        }
    }
}
