package com.phzt.waypointsync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务端入口。真正干活的只有三件事：收上传分片、响应拉取、给 OP 一条管理命令。
 *
 * <p>客户端入口在 {@link com.phzt.waypointsync.client.WaypointSyncClient}，只在客户端加载；
 * 这个类在任何环境都能跑，所以纯服务端（没人装客户端半边）也照常启动。
 */
public final class WaypointSync implements ModInitializer {
    public static final String MOD_ID = "waypointsync";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile WaypointStore store;
    private static volatile SyncConfig config;

    public static WaypointStore store() {
        return store;
    }

    public static SyncConfig config() {
        return config;
    }

    @Override
    public void onInitialize() {
        config = SyncConfig.load();
        SyncPayloads.registerCommon();

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> store = null);

        ServerPlayNetworking.registerGlobalReceiver(SyncPayloads.UploadPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ServerSync.onUpload(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SyncPayloads.RequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ServerSync.onRequest(context.player())));

        ServerCommands.register();
    }

    private void onServerStarted(MinecraftServer server) {
        store = WaypointStore.load(server);
        LOGGER.info("waypoint 仓库就绪: {} 个维度 / {} 条 ({})",
                store.dimensionCount(), store.total(), store.file());
    }

    public static void reloadConfig() {
        config = SyncConfig.load();
    }
}
