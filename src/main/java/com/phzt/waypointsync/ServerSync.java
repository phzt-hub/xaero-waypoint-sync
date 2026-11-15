package com.phzt.waypointsync;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 服务端的上传/拉取处理。上传按分片攒齐后整批替换，拉取按分片下发。 */
final class ServerSync {
    /** 同时最多允许多少片；正常一个世界几千条 waypoint 也就二三十片。 */
    private static final int MAX_PARTS = 256;
    /** 分片攒多久还没齐就丢掉，避免内存里留半截。 */
    private static final long ASSEMBLY_TIMEOUT_MS = 60_000L;

    private static final Map<UUID, Assembly> ASSEMBLIES = new HashMap<>();

    private ServerSync() {
    }

    private static final class Assembly {
        int session;
        int total;
        String[] parts;
        long startedAt;
    }

    static void onUpload(ServerPlayer player, SyncPayloads.UploadPayload payload) {
        WaypointStore store = WaypointSync.store();
        if (store == null) {
            return;
        }
        SyncConfig config = WaypointSync.config();
        if (!config.canUpload(player)) {
            tell(player, ChatFormatting.RED, "只有 OP 可以上传 waypoint（需要权限等级 " + config.opPermissionLevel + "）");
            return;
        }
        if (payload.total() <= 0 || payload.total() > MAX_PARTS
                || payload.part() < 0 || payload.part() >= payload.total()) {
            tell(player, ChatFormatting.RED, "上传分片参数不对，已丢弃");
            return;
        }

        long now = System.currentTimeMillis();
        Assembly assembly = ASSEMBLIES.get(player.getUUID());
        if (assembly == null || assembly.session != payload.session()
                || assembly.total != payload.total()
                || now - assembly.startedAt > ASSEMBLY_TIMEOUT_MS) {
            assembly = new Assembly();
            assembly.session = payload.session();
            assembly.total = payload.total();
            assembly.parts = new String[payload.total()];
            assembly.startedAt = now;
            ASSEMBLIES.put(player.getUUID(), assembly);
        }
        assembly.parts[payload.part()] = payload.json();

        for (String part : assembly.parts) {
            if (part == null) {
                return; // 还没收齐
            }
        }
        ASSEMBLIES.remove(player.getUUID());
        finishUpload(player, store, config, List.of(assembly.parts));
    }

    private static void finishUpload(ServerPlayer player, WaypointStore store, SyncConfig config, List<String> parts) {
        List<WaypointRecord> records = Wire.parseAll(parts);
        Map<String, List<WaypointRecord>> byDimension = new LinkedHashMap<>();
        int rejected = 0;
        for (WaypointRecord record : records) {
            if (record == null) continue;
            record.sanitize();
            if (record.dimension.isBlank() || record.name.isBlank()) {
                rejected++;
                continue;
            }
            List<WaypointRecord> bucket = byDimension.computeIfAbsent(record.dimension, k -> new ArrayList<>());
            if (bucket.size() >= config.maxWaypointsPerDimension) {
                rejected++;
                continue;
            }
            bucket.add(record);
        }
        if (rejected > 0) {
            WaypointSync.LOGGER.warn("{} 上传的 waypoint 中有 {} 条超出限制或字段缺失，已丢弃", player.getGameProfile().name(), rejected);
        }
        if (byDimension.size() > config.maxDimensions) {
            tell(player, ChatFormatting.RED, "维度太多（" + byDimension.size() + " > " + config.maxDimensions + "），未保存");
            return;
        }
        if (byDimension.isEmpty()) {
            tell(player, ChatFormatting.RED, "这次上传没有可用的 waypoint，服务端数据未改动");
            return;
        }

        store.replaceAll(byDimension, player.getGameProfile().name());
        int total = 0;
        StringBuilder detail = new StringBuilder();
        for (Map.Entry<String, List<WaypointRecord>> entry : byDimension.entrySet()) {
            total += entry.getValue().size();
            if (detail.length() > 0) detail.append(" / ");
            detail.append(shortDim(entry.getKey())).append(' ').append(entry.getValue().size());
        }
        WaypointSync.LOGGER.info("{} 上传 {} 条 waypoint: {}", player.getGameProfile().name(), total, detail);
        tell(player, ChatFormatting.GREEN, "已上传 " + total + " 条 waypoint，覆盖 " + byDimension.size()
                + " 个维度（" + detail + (rejected > 0 ? "，丢弃 " + rejected + " 条" : "") + "）");
    }

    static void onRequest(ServerPlayer player) {
        WaypointStore store = WaypointSync.store();
        if (store == null) {
            return;
        }
        List<WaypointRecord> records = store.flatten();
        if (records.isEmpty()) {
            tell(player, ChatFormatting.YELLOW, "服务端还没有 waypoint，等 OP 上传一次");
            return;
        }
        if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, SyncPayloads.DataPayload.TYPE)) {
            tell(player, ChatFormatting.RED, "你的客户端没装 Xaero Waypoint Sync，收不到 waypoint 数据");
            return;
        }
        List<String> chunks = Wire.chunk(records);
        for (int i = 0; i < chunks.size(); i++) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new SyncPayloads.DataPayload(i, chunks.size(), chunks.get(i)));
        }
        tell(player, ChatFormatting.GREEN, "正在发送 " + records.size() + " 条 waypoint（" + chunks.size() + " 片）");
    }

    static String shortDim(String dimension) {
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }

    static void tell(ServerPlayer player, ChatFormatting color, String text) {
        player.sendSystemMessage(Component.literal("[waypointsync] " + text).withStyle(color));
    }
}
