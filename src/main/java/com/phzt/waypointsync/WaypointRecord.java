package com.phzt.waypointsync;

/**
 * 一条 waypoint 的线上/落盘格式。
 *
 * <p>字段名直接进 JSON：服务端把这个类原样序列化写进 `world/waypointsync/waypoints.json`，
 * 客户端上传时也原样打包。所以改字段名 = 改存档格式，改之前先想好兼容。
 */
public final class WaypointRecord {
    /** Xaero 的 waypoint 名（1~32 字符，允许中文）。 */
    public String name = "";
    /** 图标上的缩写（Xaero 限制 1~3 字符）。 */
    public String initials = "";
    public int x;
    public int y;
    public int z;
    /** WaypointColor 的序号（0~19），客户端用 WaypointColor.fromIndex 还原。 */
    public int color;
    /** 传送朝向（Xaero 的 tp_yaw）。 */
    public int yaw;
    public boolean rotation;
    /** 玩家建点时带了 Y 坐标吗（false 时 y 只是占位）。 */
    public boolean yIncluded = true;
    public boolean disabled;
    /** 来源 waypoint 集的名字，只作展示/排查用，拉取时不按它分组。 */
    public String set = "";
    /** 维度 id，如 minecraft:overworld / minecraft:the_nether。 */
    public String dimension = "";

    /** 服务端入口处的清洗：Xaero 客户端对名字长度有硬限制，越界的直接截断而不是拒收整批。 */
    public void sanitize() {
        if (name == null) name = "";
        if (name.length() > 32) name = name.substring(0, 32);
        if (initials == null) initials = "";
        if (initials.length() > 4) initials = initials.substring(0, 4);
        if (set == null) set = "";
        if (set.length() > 64) set = set.substring(0, 64);
        if (dimension == null) dimension = "";
        if (dimension.length() > 128) dimension = dimension.substring(0, 128);
        // 世界边界 30M，超出的点 Xaero 也画不出来
        x = clamp(x);
        y = Math.max(-2048, Math.min(2048, y));
        z = clamp(z);
        yaw = Math.floorMod(yaw, 360);
        color = Math.floorMod(color, 20);
    }

    private static int clamp(int v) {
        return Math.max(-30_000_000, Math.min(30_000_000, v));
    }

    public WaypointRecord copy() {
        WaypointRecord r = new WaypointRecord();
        r.name = name;
        r.initials = initials;
        r.x = x;
        r.y = y;
        r.z = z;
        r.color = color;
        r.yaw = yaw;
        r.rotation = rotation;
        r.yIncluded = yIncluded;
        r.disabled = disabled;
        r.set = set;
        r.dimension = dimension;
        return r;
    }
}
