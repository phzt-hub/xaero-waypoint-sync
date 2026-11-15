package com.phzt.waypointsync.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 客户端本地命令 `/wpsync`。服务端的管理命令是 `/wpsyncadmin`，两者不重名。 */
public final class SyncClientCommands {
    private SyncClientCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("wpsync")
                .then(ClientCommands.literal("upload").executes(context -> {
                    ClientSync.upload(context.getSource());
                    return 1;
                }))
                .then(ClientCommands.literal("pull").executes(context -> {
                    ClientSync.request(context.getSource(), false);
                    return 1;
                }))
                .then(ClientCommands.literal("info").executes(context -> {
                    ClientSync.info(context.getSource());
                    return 1;
                }))
                .then(ClientCommands.literal("clear").executes(context -> {
                    ClientSync.clearLocal(context.getSource());
                    return 1;
                }))
                .then(ClientCommands.literal("config").executes(context -> {
                    // 命令是在聊天栏里执行的，直接换屏会被聊天栏的关闭逻辑顶掉，
                    // 挪到下一 tick 再开设置页。
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.execute(() -> minecraft.gui.setScreen(new SyncConfigScreen(null)));
                    return 1;
                }))
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal(
                                    "/wpsync upload — 把当前服务器全部维度的 Xaero waypoint 上传给服务端（仅 OP）\n"
                                            + "/wpsync pull — 从服务端拉取并写入「" + ClientConfig.get().setName + "」集\n"
                                            + "/wpsync info — 看状态\n"
                                            + "/wpsync clear — 清掉本地同步集\n"
                                            + "/wpsync config — 打开设置页（集名、进服自动拉取）")
                            .withStyle(ChatFormatting.GRAY));
                    return 1;
                }));
    }
}
