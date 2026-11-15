package com.phzt.waypointsync;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.Map;

/**
 * 服务端管理命令。故意不叫 `/wpsync`：那是客户端本地命令（上传/拉取走网络包），
 * 同名会让单人游戏里的两条命令互相抢输入。
 */
public final class ServerCommands {
    private ServerCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("wpsyncadmin")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("info").executes(context -> {
                            reply(context.getSource(), summary());
                            return 1;
                        }))
                        .then(Commands.literal("list").executes(context -> {
                            reply(context.getSource(), detail());
                            return 1;
                        }))
                        .then(Commands.literal("reload").executes(context -> {
                            WaypointStore store = WaypointSync.store();
                            if (store != null) store.read();
                            WaypointSync.reloadConfig();
                            reply(context.getSource(), "已重读配置与 " + (store == null ? "（无仓库）" : store.file()));
                            return 1;
                        }))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("target", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String target = StringArgumentType.getString(context, "target").trim();
                                            WaypointStore store = WaypointSync.store();
                                            if (store == null) {
                                                reply(context.getSource(), "服务端还没启动完，没有仓库");
                                                return 0;
                                            }
                                            if (target.equalsIgnoreCase("all")) {
                                                store.clear(null);
                                                reply(context.getSource(), "已清空全部 waypoint");
                                            } else {
                                                String dimension = target.contains(":") ? target : "minecraft:" + target;
                                                store.clear(dimension);
                                                reply(context.getSource(), "已清空 " + dimension);
                                            }
                                            return 1;
                                        })))));
    }

    private static String summary() {
        WaypointStore store = WaypointSync.store();
        if (store == null) {
            return "服务端还没启动完，没有仓库";
        }
        SyncConfig config = WaypointSync.config();
        return "仓库: " + store.dimensionCount() + " 个维度 / " + store.total() + " 条"
                + "；上传权限等级 " + config.opPermissionLevel
                + "；单维度上限 " + config.maxWaypointsPerDimension;
    }

    private static String detail() {
        WaypointStore store = WaypointSync.store();
        if (store == null) {
            return "服务端还没启动完，没有仓库";
        }
        Map<String, WaypointStore.DimensionEntry> snapshot = store.snapshot();
        if (snapshot.isEmpty()) {
            return "仓库是空的。OP 在客户端执行 /wpsync upload 上传。";
        }
        StringBuilder sb = new StringBuilder("维度明细:");
        for (Map.Entry<String, WaypointStore.DimensionEntry> entry : snapshot.entrySet()) {
            WaypointStore.DimensionEntry value = entry.getValue();
            sb.append('\n').append("  ").append(entry.getKey())
                    .append(" — ").append(value.waypoints.size()).append(" 条")
                    .append("，上传者 ").append(value.uploader.isEmpty() ? "?" : value.uploader)
                    .append("，更新于 ").append(java.time.Instant.ofEpochMilli(value.updatedAt));
        }
        sb.append('\n').append("（文件: ").append(store.file()).append('）');
        return sb.toString();
    }

    private static void reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
    }
}
