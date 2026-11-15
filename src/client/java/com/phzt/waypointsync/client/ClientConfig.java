package com.phzt.waypointsync.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 客户端配置：`config/waypointsync-client.json`。 */
public final class ClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("waypointsync/client");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("waypointsync-client.json");

    /** 拉取时写进哪个 waypoint 集。这个集是 mod 独占的，每次拉取整集重写。 */
    public String setName = "服务器同步";
    /** 进服务器后自动拉一次（延迟 2 秒，避开登录包风暴）。 */
    public boolean autoPullOnJoin = true;

    private static ClientConfig instance = new ClientConfig();

    public static ClientConfig get() {
        return instance;
    }

    public static void load() {
        ClientConfig config = new ClientConfig();
        try {
            if (Files.isRegularFile(FILE)) {
                ClientConfig read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), ClientConfig.class);
                if (read != null) config = read;
            }
            if (config.setName == null || config.setName.isBlank()) {
                config.setName = "服务器同步";
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("读取 {} 失败，使用默认配置: {}", FILE, e.toString());
        }
        instance = config;
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("写入 {} 失败: {}", FILE, e.toString());
        }
    }
}
