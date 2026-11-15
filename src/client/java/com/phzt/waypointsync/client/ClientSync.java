package com.phzt.waypointsync.client;

import com.phzt.waypointsync.SyncPayloads;
import com.phzt.waypointsync.WaypointRecord;
import com.phzt.waypointsync.Wire;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 客户端的上传/拉取动作。命令与网络回调都从这里进。 */
public final class ClientSync {
    private static final int MAX_PARTS = 256;
    private static final List<String> INCOMING = new ArrayList<>();
    private static int incomingTotal = -1;
    private static int autoPullCountdown = -1;

    private ClientSync() {
    }

    public static void upload(FabricClientCommandSource source) {
        if (!WaypointSyncClient.XAERO_INSTALLED) {
            feedback(source, ChatFormatting.RED, "没有装 Xaero's Minimap，读不到 waypoint");
            return;
        }
        if (!XaeroBridge.available()) {
            feedback(source, ChatFormatting.RED, "世界还没加载完，稍等一下再试");
            return;
        }
        List<WaypointRecord> records = XaeroBridge.collectAll();
        if (records.isEmpty()) {
            feedback(source, ChatFormatting.YELLOW, "当前服务器的 Xaero 里没有 waypoint");
            return;
        }
        if (!canSend(SyncPayloads.UploadPayload.TYPE)) {
            feedback(source, ChatFormatting.RED, "服务端没有装 Xaero Waypoint Sync（或还没握手完）");
            return;
        }
        List<String> chunks = Wire.chunk(records);
        if (chunks.size() > MAX_PARTS) {
            feedback(source, ChatFormatting.RED, "waypoint 太多（" + records.size() + " 条），超过单次上传上限");
            return;
        }
        int session = (int) (System.nanoTime() & 0x7fffffff);
        for (int i = 0; i < chunks.size(); i++) {
            ClientPlayNetworking.send(new SyncPayloads.UploadPayload(session, i, chunks.size(), chunks.get(i)));
        }
        feedback(source, ChatFormatting.GREEN, "已发送 " + records.size() + " 条 waypoint（" + chunks.size() + " 片），等服务端确认");
        WaypointSyncClient.LOGGER.info("上传 {} 条 waypoint, {} 片", records.size(), chunks.size());
    }

    public static void request(FabricClientCommandSource source, boolean silent) {
        if (!WaypointSyncClient.XAERO_INSTALLED) {
            if (!silent) feedback(source, ChatFormatting.RED, "没有装 Xaero's Minimap，拿到数据也显示不了");
            return;
        }
        if (!canSend(SyncPayloads.RequestPayload.TYPE)) {
            if (!silent) feedback(source, ChatFormatting.RED, "服务端没有装 Xaero Waypoint Sync（或还没握手完）");
            return;
        }
        INCOMING.clear();
        incomingTotal = -1;
        ClientPlayNetworking.send(new SyncPayloads.RequestPayload());
        if (!silent) feedback(source, ChatFormatting.GRAY, "正在请求服务端 waypoint…");
    }

    /** 服务端数据分片到齐。 */
    public static void onData(SyncPayloads.DataPayload payload) {
        if (payload.total() <= 0 || payload.total() > MAX_PARTS) return;
        if (!WaypointSyncClient.XAERO_INSTALLED) return;
        if (incomingTotal != payload.total()) {
            INCOMING.clear();
            incomingTotal = payload.total();
        }
        while (INCOMING.size() < payload.total()) INCOMING.add(null);
        INCOMING.set(payload.part(), payload.json());
        for (String part : INCOMING) {
            if (part == null) return;
        }
        List<String> parts = new ArrayList<>(INCOMING);
        INCOMING.clear();
        incomingTotal = -1;

        List<WaypointRecord> records = Wire.parseAll(parts);
        XaeroBridge.ApplyResult result = null;
        try {
            result = XaeroBridge.apply(records);
        } catch (Throwable t) {
            WaypointSyncClient.LOGGER.error("写入 Xaero waypoint 失败", t);
        }
        if (result == null) {
            print(ChatFormatting.RED, "收到 " + records.size() + " 条 waypoint，但世界还没加载完，稍后 /wpsync pull 重试");
        } else {
            String message = "已同步 " + result.written() + " 条 waypoint 到「" + ClientConfig.get().setName + "」集";
            if (result.skipped() > 0) {
                message += "，跳过 " + result.skipped() + " 条本地已有的相同导航点";
            }
            print(ChatFormatting.GREEN, message);
        }
    }

    public static void info(FabricClientCommandSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.player != null;
        feedback(source, ChatFormatting.GRAY, "Xaero: " + (WaypointSyncClient.XAERO_INSTALLED ? "已安装" : "未安装")
                + "；当前世界: " + (inWorld ? "已进入" : "未进入")
                + "；集名: " + ClientConfig.get().setName
                + "；进服自动拉取: " + (ClientConfig.get().autoPullOnJoin ? "开" : "关"));
        if (WaypointSyncClient.XAERO_INSTALLED && XaeroBridge.available()) {
            feedback(source, ChatFormatting.GRAY, "本地可上传的 waypoint: " + XaeroBridge.collectAll().size() + " 条");
        }
    }

    public static void clearLocal(FabricClientCommandSource source) {
        if (!WaypointSyncClient.XAERO_INSTALLED) {
            feedback(source, ChatFormatting.RED, "没有装 Xaero's Minimap");
            return;
        }
        int removed = XaeroBridge.clearLocal();
        feedback(source, ChatFormatting.GREEN, "已从本地「" + ClientConfig.get().setName + "」集清掉 " + removed + " 条");
    }

    public static void onJoin() {
        autoPullCountdown = ClientConfig.get().autoPullOnJoin ? 40 : -1;
    }

    public static void tick() {
        if (autoPullCountdown < 0) return;
        if (--autoPullCountdown == 0) {
            autoPullCountdown = -1;
            if (Minecraft.getInstance().player != null) {
                request(null, true);
            }
        }
    }

    private static boolean canSend(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return false;
        try {
            return ClientPlayNetworking.canSend(type);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void feedback(FabricClientCommandSource source, ChatFormatting color, String text) {
        if (source != null) {
            source.sendFeedback(Component.literal("[waypointsync] " + text).withStyle(color));
        } else {
            print(color, text);
        }
    }

    private static void print(ChatFormatting color, String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("[waypointsync] " + text).withStyle(color));
        }
    }
}
