package com.phzt.waypointsync.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 本 mod 的设置页：Mod Menu 里这个 mod 的齿轮，或者 `/wpsync config`。
 *
 * <p>只有两样是真正要存的（同步集名称、进服自动拉取），其余三个按钮是把命令搬到界面上：
 * 拉取、上传（服务端再校验 OP）、清空本地同步集（要连点两次）。
 *
 * <p>聊天栏在界面打开时是看不见的，所以 {@link ClientSync#lastStatus()} 会在这里再显示一次。
 */
public final class SyncConfigScreen extends Screen {
    private static final int HEADER = 0xFF9AA4AE;
    private static final int NOTE = 0xFF8A949E;
    /** 清空要连点两次，前后两次之间最多隔这么久。 */
    private static final long CLEAR_CONFIRM_MILLIS = 5000;

    private final Screen parent;
    private EditBox nameField;
    private Button autoPullButton;
    private Button clearButton;
    private boolean draftAutoPull;
    private String error;
    private long clearArmedUntil;

    public SyncConfigScreen(Screen parent) {
        super(Component.literal("Xaero Waypoint Sync"));
        this.parent = parent;
        this.draftAutoPull = ClientConfig.get().autoPullOnJoin;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;

        this.nameField = new EditBox(this.font, left, 78, 200, 18, Component.literal("同步集名称"));
        this.nameField.setMaxLength(ClientConfig.MAX_SET_NAME_LENGTH);
        this.nameField.setValue(ClientConfig.get().setName);
        this.nameField.setTooltip(Tooltip.create(Component.literal("拉取到的 waypoint 写进这个集，每次拉取整集重写，不碰你自己的集")));
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        this.autoPullButton = this.addRenderableWidget(Button.builder(autoPullLabel(), b -> {
            this.draftAutoPull = !this.draftAutoPull;
            b.setMessage(autoPullLabel());
        }).bounds(left, 124, 200, 20)
                .tooltip(Tooltip.create(Component.literal("进服务器两秒后自动拉一次，免得每次手动敲命令")))
                .build());

        int buttonWidth = 64;
        this.addRenderableWidget(Button.builder(Component.literal("立即拉取"), b -> ClientSync.request(null, false))
                .bounds(left, 152, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.literal("从服务端取 waypoint，结果写在下面这一行")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("上传"), b -> ClientSync.upload(null))
                .bounds(left + buttonWidth + 4, 152, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.literal("把当前服务器所有维度的 waypoint 推给服务端；只有 OP 成功，回执看聊天栏")))
                .build());
        this.clearButton = this.addRenderableWidget(Button.builder(Component.literal("清空同步集"), b -> this.onClearPressed())
                .bounds(left + (buttonWidth + 4) * 2, 152, buttonWidth + 4, 20)
                .tooltip(Tooltip.create(Component.literal("只删本地那个同步集，不影响你自己建的集；删完可以再拉")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("完成"), b -> this.saveAndClose())
                .bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("取消"), b -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 100, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.clearArmedUntil > 0 && System.currentTimeMillis() > this.clearArmedUntil) {
            this.clearArmedUntil = 0;
            this.clearButton.setMessage(Component.literal("清空同步集"));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 先铺一层暗底：26.3 的菜单背景在城市/天空这种亮场景上几乎看不出层次，
        // 不压暗的话浅灰字直接糊掉。铺完再让 super 画控件，控件在上层。
        graphics.fill(0, 0, this.width, this.height, 0xC0121212);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int left = centerX - 100;

        graphics.centeredText(this.font, this.getTitle(), centerX, 20, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("OP 上传 waypoint，所有人拉取到自己 Xaero 里"),
                centerX, 34, HEADER);
        graphics.centeredText(this.font, statusLine(), centerX, 50, 0xFFFFFFFF);

        graphics.text(this.font, Component.literal("同步集名称"), left, 66, HEADER);
        graphics.text(this.font, Component.literal("集名不能带英文冒号；改名不会删掉旧集"), left, 100, NOTE);

        Component line;
        int color;
        if (this.error != null) {
            line = Component.literal(this.error);
            color = 0xFFFF5555;
        } else {
            line = Component.literal("最近一次操作：" + ClientSync.lastStatus());
            color = ClientSync.lastStatusFailed() ? 0xFFFF5555 : 0xFF7FD98A;
        }
        int maxWidth = this.width - 16;
        String text = this.font.plainSubstrByWidth(line.getString(), maxWidth);
        if (text.length() < line.getString().length()) text = text + "…";
        graphics.text(this.font, Component.literal(text), 8, 180, color);

        graphics.centeredText(this.font, Component.literal("服务端的权限等级与单维度上限在 config/waypointsync-server.json"),
                centerX, 196, NOTE);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    /** 连点两次才真清空：第一次只是把按钮改成确认文案。 */
    private void onClearPressed() {
        long now = System.currentTimeMillis();
        if (this.clearArmedUntil > 0 && now <= this.clearArmedUntil) {
            this.clearArmedUntil = 0;
            this.clearButton.setMessage(Component.literal("清空同步集"));
            ClientSync.clearLocal(null);
            return;
        }
        this.clearArmedUntil = now + CLEAR_CONFIRM_MILLIS;
        this.clearButton.setMessage(Component.literal("再点一次确认"));
        ClientSync.setStatus("5 秒内再点一次，才会清空本地「" + ClientConfig.get().setName + "」集", false);
    }

    private void saveAndClose() {
        String name = this.nameField == null ? ClientConfig.get().setName : this.nameField.getValue();
        String problem = ClientConfig.validateSetName(name);
        if (problem != null) {
            this.error = problem;
            return;
        }
        String before = ClientConfig.get().setName;
        ClientConfig.apply(name, this.draftAutoPull);
        if (!before.equals(ClientConfig.get().setName)) {
            ClientSync.setStatus("集名已改为「" + ClientConfig.get().setName + "」；旧集「" + before + "」不会自动删",
                    false);
        }
        this.error = null;
        this.onClose();
    }

    private Component autoPullLabel() {
        return Component.literal("进服自动拉取：" + (this.draftAutoPull ? "开" : "关"));
    }

    private Component statusLine() {
        boolean xaero = WaypointSyncClient.XAERO_INSTALLED;
        boolean world = this.minecraft.player != null;
        boolean server = ClientSync.serverAvailable();
        return Component.literal("Xaero ").withStyle(ChatFormatting.GRAY)
                .append(flag(xaero, "已安装", "未安装"))
                .append(Component.literal("    世界 ").withStyle(ChatFormatting.GRAY))
                .append(flag(world, "已进入", "未进入"))
                .append(Component.literal("    服务端 ").withStyle(ChatFormatting.GRAY))
                .append(flag(server, "已连接", "未连接"));
    }

    private static Component flag(boolean ok, String yes, String no) {
        return Component.literal(ok ? yes : no)
                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
