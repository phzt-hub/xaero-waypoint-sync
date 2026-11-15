package com.phzt.waypointsync.client;

import com.phzt.waypointsync.WaypointRecord;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Xaero 读写桥。
 *
 * <p>只在客户端加载，并且只有 {@link WaypointSyncClient#XAERO_INSTALLED} 为 true 时才会被碰；
 * 没有 Xaero 的客户端不会加载到这个类（JVM 按需加载类，静态引用不会提前触发）。
 *
 * <p>读：`MinimapSession.getWorldManager().getCurrentWorld().getContainer().getRoot()` 下所有
 * MinimapWorld（= 当前服务器的所有维度），遍历每个世界的每个 waypoint 集。
 *
 * <p>写：拉取的数据只落在 {@link ClientConfig#setName} 这一个集里，先 clear 再整集重填，
 * 不动玩家自己建的集。重填前先扫一遍这个维度里玩家已有的 waypoint：名字 + 坐标完全相同的
 * 直接跳过，不再塞一份重复的点进同步集。
 */
final class XaeroBridge {
    private XaeroBridge() {
    }

    /** 一次拉取的结果。 */
    record ApplyResult(int written, int skipped) {
    }

    static MinimapSession session() {
        return BuiltInHudModules.MINIMAP.getCurrentSession();
    }

    static boolean available() {
        MinimapSession session = session();
        return session != null && session.getWorldManager().getCurrentWorld() != null;
    }

    /** 收集当前服务器所有维度的全部 waypoint（跳过临时点，Xaero 的临时点不是玩家建的）。 */
    static List<WaypointRecord> collectAll() {
        List<WaypointRecord> out = new ArrayList<>();
        MinimapSession session = session();
        if (session == null) return out;
        MinimapWorld current = session.getWorldManager().getCurrentWorld();
        if (current == null) return out;
        MinimapWorldContainer root = current.getContainer().getRoot();
        for (MinimapWorld world : root.getAllWorldsIterable()) {
            ResourceKey<Level> key = world.getDimId();
            if (key == null) continue;
            String dimension = key.identifier().toString();
            for (WaypointSet set : world.getIterableWaypointSets()) {
                for (Waypoint waypoint : set.getWaypoints()) {
                    if (waypoint.isTemporary()) continue;
                    WaypointRecord record = new WaypointRecord();
                    record.name = waypoint.getName();
                    record.initials = waypoint.getInitials();
                    record.x = waypoint.getX();
                    record.y = waypoint.getY();
                    record.z = waypoint.getZ();
                    record.color = waypoint.getWaypointColor().ordinal();
                    record.yaw = waypoint.getYaw();
                    record.rotation = waypoint.isRotation();
                    record.yIncluded = waypoint.isYIncluded();
                    record.disabled = waypoint.isDisabled();
                    record.set = set.getName();
                    record.dimension = dimension;
                    out.add(record);
                }
            }
        }
        return out;
    }

    /**
     * 把服务端数据写进「服务器同步」集。
     *
     * <p>去重口径：同一维度里，玩家已有 waypoint 与本条「名字 + X + Y（没带 Y 时按 ~ 算）+ Z」
     * 完全相同就跳过。颜色/朝向不参与比较——那是外观，不是「不同的点」。
     *
     * @return 写入条数与跳过条数；{@code null} 表示当前没有可写的世界（还在加载）。
     */
    static ApplyResult apply(List<WaypointRecord> records) {
        MinimapSession session = session();
        if (session == null) return null;
        MinimapWorld current = session.getWorldManager().getCurrentWorld();
        if (current == null) return null;
        MinimapWorldContainer root = current.getContainer().getRoot();

        Map<String, MinimapWorld> worldsByDimension = new HashMap<>();
        for (MinimapWorld world : root.getAllWorldsIterable()) {
            ResourceKey<Level> key = world.getDimId();
            if (key != null) worldsByDimension.put(key.identifier().toString(), world);
        }
        Map<String, List<WaypointRecord>> grouped = new LinkedHashMap<>();
        for (WaypointRecord record : records) {
            if (!record.dimension.isEmpty()) {
                grouped.computeIfAbsent(record.dimension, k -> new ArrayList<>()).add(record);
            }
        }

        String setName = ClientConfig.get().setName;
        int written = 0;
        int skipped = 0;
        for (Map.Entry<String, MinimapWorld> entry : worldsByDimension.entrySet()) {
            MinimapWorld world = entry.getValue();
            List<WaypointRecord> forDimension = grouped.get(entry.getKey());
            WaypointSet existing = world.getWaypointSet(setName);
            if (forDimension == null) {
                // 服务端已经没有这个维度的数据了：把上一次同步留下的集清掉
                if (existing != null && !existing.isEmpty()) {
                    existing.clear();
                    saveWorld(session, world);
                }
                continue;
            }

            // 先收玩家自己已有的点（不含同步集本身），再整集重填
            Set<String> seen = new HashSet<>();
            for (WaypointSet set : world.getIterableWaypointSets()) {
                if (set == existing) continue;
                for (Waypoint waypoint : set.getWaypoints()) {
                    seen.add(key(waypoint));
                }
            }

            List<WaypointRecord> toAdd = new ArrayList<>();
            for (WaypointRecord record : forDimension) {
                if (!seen.add(key(record))) {
                    skipped++;
                    continue;
                }
                toAdd.add(record);
            }
            if (toAdd.isEmpty() && existing == null) {
                // 全部都被去重跳过了，就别在玩家的集列表里凭空建一个空集
                continue;
            }
            WaypointSet target = existing;
            if (target == null) {
                target = WaypointSet.Builder.begin().setName(setName).build();
                world.addWaypointSet(target);
            }
            target.clear();
            for (WaypointRecord record : toAdd) {
                Waypoint waypoint = new Waypoint(record.x, record.y, record.z,
                        record.name, record.initials,
                        WaypointColor.fromIndex(record.color), WaypointPurpose.NORMAL,
                        false, record.yIncluded);
                waypoint.setRotation(record.rotation);
                waypoint.setYaw(record.yaw);
                waypoint.setDisabled(record.disabled);
                target.add(waypoint);
                written++;
            }
            saveWorld(session, world);
        }
        return new ApplyResult(written, skipped);
    }

    /** 清掉所有维度里由本 mod 维护的那个集（`/wpsync clear` 用）。 */
    static int clearLocal() {
        MinimapSession session = session();
        if (session == null) return 0;
        MinimapWorld current = session.getWorldManager().getCurrentWorld();
        if (current == null) return 0;
        String setName = ClientConfig.get().setName;
        int removed = 0;
        for (MinimapWorld world : current.getContainer().getRoot().getAllWorldsIterable()) {
            WaypointSet set = world.getWaypointSet(setName);
            if (set == null || set.isEmpty()) continue;
            removed += set.size();
            set.clear();
            saveWorld(session, world);
        }
        return removed;
    }

    private static String key(WaypointRecord record) {
        return record.name + '' + record.x + ''
                + (record.yIncluded ? Integer.toString(record.y) : "~") + '' + record.z;
    }

    private static String key(Waypoint waypoint) {
        return waypoint.getName() + '' + waypoint.getX() + ''
                + (waypoint.isYIncluded() ? Integer.toString(waypoint.getY()) : "~") + '' + waypoint.getZ();
    }

    private static void saveWorld(MinimapSession session, MinimapWorld world) {
        try {
            session.getWorldManagerIO().saveWorld(world);
        } catch (IOException | RuntimeException e) {
            WaypointSyncClient.LOGGER.warn("保存 waypoint 世界 {} 失败: {}", world.getFullPath(), e.toString());
        }
    }
}
