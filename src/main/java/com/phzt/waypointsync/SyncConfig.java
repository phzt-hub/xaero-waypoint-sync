package com.phzt.waypointsync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 服务端配置：`config/waypointsync-server.json`。
 *
 * <p>缺文件时自动写一份默认值；字段缺省时保留默认值（Gson 只覆盖 JSON 里出现的字段）。
 */
public final class SyncConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("waypointsync-server.json");

    /** 允许上传所需的原版权限等级：2 = OP，4 = 服务器控制台级。 */
    public int opPermissionLevel = 2;
    /** 调试用：放开给所有玩家上传（默认关）。 */
    public boolean allowNonOpUpload = false;
    /** 单个维度最多存多少条，防止有人拿大文件把服务器撑爆。 */
    public int maxWaypointsPerDimension = 4000;
    /** 维度数上限。 */
    public int maxDimensions = 64;

    public static SyncConfig load() {
        SyncConfig config = new SyncConfig();
        try {
            if (Files.isRegularFile(FILE)) {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                SyncConfig read = GSON.fromJson(json, SyncConfig.class);
                if (read != null) {
                    read.opPermissionLevel = Math.max(0, Math.min(4, read.opPermissionLevel));
                    read.maxWaypointsPerDimension = Math.max(1, read.maxWaypointsPerDimension);
                    read.maxDimensions = Math.max(1, read.maxDimensions);
                    config = read;
                }
            } else {
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, GSON.toJson(config), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            WaypointSync.LOGGER.warn("读取 {} 失败，使用默认配置: {}", FILE, e.toString());
        }
        return config;
    }

    public boolean canUpload(net.minecraft.server.level.ServerPlayer player) {
        if (allowNonOpUpload) return true;
        // 26.3 起权限换成了 PermissionSet 模型：等级 N 等价于「拥有第 N 级命令权限」
        return switch (opPermissionLevel) {
            case 0 -> true;
            case 1 -> player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
            case 2 -> player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
            case 3 -> player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
            default -> player.permissions().hasPermission(Permissions.COMMANDS_OWNER);
        };
    }
}
