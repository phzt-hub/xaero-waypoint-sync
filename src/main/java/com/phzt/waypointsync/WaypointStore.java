package com.phzt.waypointsync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 服务端的 waypoint 仓库，落在 `<存档>/waypointsync/waypoints.json`。
 *
 * <p>按维度分桶；上传是一次性整体覆盖（和「OP 把当前世界全部 waypoint 推上来」的语义一致），
 * 所以不会出现新旧两份拼在一起的半吊子状态。
 */
public final class WaypointStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 落盘时的顶层结构。 */
    private static final class Root {
        int version = 1;
        Map<String, DimensionEntry> dimensions = new TreeMap<>();
    }

    public static final class DimensionEntry {
        public String uploader = "";
        public long updatedAt;
        public List<WaypointRecord> waypoints = new ArrayList<>();
    }

    private final Path file;
    private final Root root = new Root();

    private WaypointStore(Path file) {
        this.file = file;
    }

    public static WaypointStore load(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("waypointsync").resolve("waypoints.json");
        WaypointStore store = new WaypointStore(path);
        store.read();
        return store;
    }

    public Path file() {
        return file;
    }

    public synchronized void read() {
        root.dimensions.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Root read = GSON.fromJson(json, Root.class);
            if (read != null && read.dimensions != null) {
                root.dimensions.putAll(read.dimensions);
                root.dimensions.values().removeIf(e -> e == null || e.waypoints == null);
            }
        } catch (IOException | RuntimeException e) {
            WaypointSync.LOGGER.warn("读取 {} 失败: {}", file, e.toString());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("waypoints.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            WaypointSync.LOGGER.error("写入 {} 失败: {}", file, e.toString());
        }
    }

    /** 整体覆盖：传进来的维度就是全部，没出现在 map 里的维度会被删掉。 */
    public synchronized void replaceAll(Map<String, List<WaypointRecord>> byDimension, String uploader) {
        root.dimensions.clear();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, List<WaypointRecord>> e : byDimension.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            DimensionEntry entry = new DimensionEntry();
            entry.uploader = uploader;
            entry.updatedAt = now;
            entry.waypoints = new ArrayList<>(e.getValue());
            root.dimensions.put(e.getKey(), entry);
        }
        save();
    }

    public synchronized void clear(String dimension) {
        if (dimension == null) {
            root.dimensions.clear();
        } else {
            root.dimensions.remove(dimension);
        }
        save();
    }

    public synchronized Map<String, DimensionEntry> snapshot() {
        Map<String, DimensionEntry> copy = new LinkedHashMap<>();
        for (Map.Entry<String, DimensionEntry> e : root.dimensions.entrySet()) {
            DimensionEntry src = e.getValue();
            DimensionEntry dst = new DimensionEntry();
            dst.uploader = src.uploader;
            dst.updatedAt = src.updatedAt;
            dst.waypoints = new ArrayList<>(src.waypoints.size());
            for (WaypointRecord r : src.waypoints) dst.waypoints.add(r.copy());
            copy.put(e.getKey(), dst);
        }
        return copy;
    }

    /** 拉平给客户端用：每条记录里自带 dimension 字段。 */
    public synchronized List<WaypointRecord> flatten() {
        List<WaypointRecord> out = new ArrayList<>();
        for (Map.Entry<String, DimensionEntry> e : root.dimensions.entrySet()) {
            for (WaypointRecord r : e.getValue().waypoints) {
                WaypointRecord copy = r.copy();
                copy.dimension = e.getKey();
                out.add(copy);
            }
        }
        return out;
    }

    public synchronized int total() {
        int n = 0;
        for (DimensionEntry e : root.dimensions.values()) n += e.waypoints.size();
        return n;
    }

    public synchronized int dimensionCount() {
        return root.dimensions.size();
    }
}
