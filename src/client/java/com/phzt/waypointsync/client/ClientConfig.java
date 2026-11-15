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

/**
 * 客户端配置：`config/waypointsync-client.json`。
 *
 * <p>游戏里的入口是 {@link SyncConfigScreen}（Mod Menu 的齿轮，或 `/wpsync config`），
 * 这个文件只是它落盘的地方，改完立刻生效。
 */
public final class ClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("waypointsync/client");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("waypointsync-client.json");

    /** 集名的默认值，也是校验失败时的兜底。 */
    public static final String DEFAULT_SET_NAME = "服务器同步";
    /** Xaero 的 waypoints.txt 用冒号分隔字段，集名太长也没意义，这里限死。 */
    public static final int MAX_SET_NAME_LENGTH = 32;

    /** 拉取时写进哪个 waypoint 集。这个集是 mod 独占的，每次拉取整集重写。 */
    public String setName = DEFAULT_SET_NAME;
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
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("读取 {} 失败，使用默认配置: {}", FILE, e.toString());
        }
        if (validateSetName(config.setName) != null) {
            LOGGER.warn("配置里的集名 {} 不合法，回落到默认值 {}", config.setName, DEFAULT_SET_NAME);
            config.setName = DEFAULT_SET_NAME;
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

    /**
     * 集名能不能用：Xaero 的 waypoints.txt 拿冒号分字段，集名里再放冒号会把文件写坏；
     * `gui.xaero_default` 之类是 Xaero 的内置键，占了会把玩家自己的默认集当同步集清掉。
     *
     * @return 错误文案；{@code null} 表示这个集名可以用
     */
    public static String validateSetName(String name) {
        if (name == null || name.isBlank()) return "集名不能为空";
        String trimmed = name.trim();
        if (trimmed.length() > MAX_SET_NAME_LENGTH) {
            return "集名最多 " + MAX_SET_NAME_LENGTH + " 个字符";
        }
        if (trimmed.indexOf(':') >= 0) return "集名不能包含英文冒号「:」，Xaero 用它分隔字段";
        if (trimmed.startsWith("gui.xaero")) return "「gui.xaero…」是 Xaero 的内置集，换个名字";
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == 0x7F) return "集名不能包含控制字符";
        }
        return null;
    }

    /** 设置页按「完成」：写进内存并落盘。传进来的集名必须已经过 {@link #validateSetName}。 */
    public static void apply(String setName, boolean autoPullOnJoin) {
        ClientConfig config = get();
        config.setName = setName.trim();
        config.autoPullOnJoin = autoPullOnJoin;
        save();
    }
}
