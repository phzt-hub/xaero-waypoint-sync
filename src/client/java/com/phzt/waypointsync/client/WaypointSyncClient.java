package com.phzt.waypointsync.client;

import com.phzt.waypointsync.SyncPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口：注册 `/wpsync`、接收服务端数据分片、进服自动拉取。
 *
 * <p>没有 Xaero 的客户端也照常加载（只是 `/wpsync` 会提示缺前置），
 * 所以 {@link #XAERO_INSTALLED} 为 false 时不会碰 {@link XaeroBridge}。
 */
public final class WaypointSyncClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("waypointsync/client");
    public static final boolean XAERO_INSTALLED = FabricLoader.getInstance().isModLoaded("xaerominimap");

    @Override
    public void onInitializeClient() {
        ClientConfig.load();
        if (!XAERO_INSTALLED) {
            LOGGER.warn("没检测到 Xaero's Minimap（xaerominimap）：waypoint 读写不可用");
        }

        ClientPlayNetworking.registerGlobalReceiver(SyncPayloads.DataPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientSync.onData(payload)));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                SyncClientCommands.register(dispatcher));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientSync.onJoin());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientSync.tick());
    }
}
